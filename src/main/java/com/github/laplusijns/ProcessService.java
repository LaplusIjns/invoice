package com.github.laplusijns;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.Endpoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks.EmitResult;

@Endpoint
@AnonymousAllowed
public class ProcessService {

    ChatClient chatClient;
    InvoiceService invoiceService;
    ImageCache imageCache;
    InvoiceChannels invoiceChannels;
    PaddleXOcrClient paddleXOcrClient;
    InvoiceQrCodeReader invoiceQrCodeReader;
    RetryTemplate retryTemplate;
    UserCache userCache;
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();

    private static final String AI_PROMPT = """
			你是一個專業的 OCR 與台灣發票分析 AI。
			任務：分析我提供的台灣發票圖片，準確抽取以下資訊：
			發票號碼（Invoice Number）
			格式：兩個英文大寫字母 + 減號 + 八位數字，例如 "AB-11223344"
			發票日期（Invoice Date）
			格式：台灣民國年 + 月份區間，例如 "104年05-06月"
			請以 JSON 格式回傳結果，結構如下：
			{
			  "invoice_number": "抽取到的發票號碼",
			  "invoice_date": "抽取到的發票日期"
			}
			注意事項：
			只輸出 JSON，不要額外文字。
			日期必須保持台灣民國年 + 月份區間格式。
			發票號碼必須符合兩個英文大寫字母 + 減號 + 八位數字。
			如果找不到對應欄位，請填 "N/A"。
			如果 AI 偵測到的號碼格式不正確，也請填 "N/A"。
			""";

    private static Logger log = LoggerFactory.getLogger(ProcessService.class);

    static final String NA = "N/A";

    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    @NonNull
    public String jsessionId() {
        return currentSessionId();
    }

    @NonNull
    private String currentSessionId() {
        final VaadinServletRequest vaadinRequest = (VaadinServletRequest) VaadinService.getCurrentRequest();
        final HttpServletRequest request = vaadinRequest.getHttpServletRequest();
        final HttpSession vaadinSession = request.getSession();
        return vaadinSession.getId();
    }

    @NonNull
    public Flux<@NonNull InvoiceDTO> invoiceSubscription(final String jsessionid) {
        return invoiceChannels.invoiceSubscription(jsessionid);
    }

    public ProcessService(
            final ChatClient chatClient,
            final InvoiceService invoiceService,
            final ImageCache imageCache,
            final InvoiceChannels invoiceChannels,
            final UserCache userCache,
            final PaddleXOcrClient paddleXOcrClient,
            final InvoiceQrCodeReader invoiceQrCodeReader) {
        super();
        this.chatClient = chatClient;
        this.invoiceService = invoiceService;
        this.imageCache = imageCache;
        this.invoiceChannels = invoiceChannels;
        this.userCache = userCache;
        this.paddleXOcrClient = paddleXOcrClient;
        this.invoiceQrCodeReader = invoiceQrCodeReader;

        final var retryPolicy = RetryPolicy.builder()
                .includes(Exception.class)
                .maxRetries(10)
                .delay(Duration.ofSeconds(70))
                .build();
        this.retryTemplate = new RetryTemplate(retryPolicy);
        workerExecutor.submit(this::consumeQueue);
    }

    private void consumeQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                final Runnable task = taskQueue.take(); // 等待任務
                retryTemplate.execute(() -> {
                    task.run();
                    return null;
                });
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("worker retry failed", e);
            }
        }
    }

    public @NonNull List<@NonNull InvoiceDTO> data(@NonNull final String jsessionid) {
        final var result = userCache.get(jsessionid);
        if (result != null) {
            return result.getData();
        }
        return List.of();
    }

    public void process(@NonNull final String base64Image, @NonNull final String jsessionId) {

        final var userData = userCache.get(jsessionId);
        if (userData == null) {
            return;
        }

        final List<InvoiceDTO> data = userData.getData();

        log.info("task start {}", new Date());
        // 把 OCR 任務加入隊列，不直接執行
        final boolean bool = taskQueue.offer(() -> {
            try {
                final String[] parts = base64Image.split(";base64,");
                final String mimeTypeString = parts[0].replace("data:", "");
                final byte[] imageBytes = Base64.getDecoder().decode(parts[1]);
                final byte[] resizeBytes = resizePng(imageBytes, 300);
                final MimeType mimeType = MimeTypeUtils.parseMimeType(mimeTypeString);

                final Invoice invoice = recognizeInvoice(imageBytes, mimeType);
                final List<String> qrInvoiceNumbers = invoiceQrCodeReader.readInvoiceNumbers(imageBytes);

                final InvoiceResult result = checkInvoiceResult(invoice);
                final String uuid = UUID.randomUUID().toString();
                imageCache.put(uuid, imageBytes);
                imageCache.putThumbnail(uuid, resizeBytes);
                final String key = UUID.randomUUID().toString();

                final InvoiceDTO invoiceDTO = new InvoiceDTO(
                        key, invoice.invoiceNumber, invoice.invoiceDate, qrInvoiceNumbers, result, uuid);
                data.add(invoiceDTO);
                log.info("{}", jsessionId);
                final EmitResult emitResult = invoiceChannels.tryEmitNext(jsessionId, invoiceDTO);
                log.info("emitResult {}", emitResult);

            } catch (Exception e) {
                log.error("invoice OCR task failed", e);
            }
        });

        if (!bool) {
            log.error("序列出錯");
        }
    }

    public boolean reprocess(@NonNull final String key) {
        final String jsessionId = currentSessionId();
        final UserData userData = userCache.get(jsessionId);
        if (userData == null) {
            return false;
        }

        final List<InvoiceDTO> data = userData.getData();
        InvoiceDTO existingInvoice = null;
        synchronized (data) {
            for (final InvoiceDTO invoice : data) {
                if (invoice.key().equals(key)) {
                    existingInvoice = invoice;
                    break;
                }
            }
        }
        if (existingInvoice == null) {
            return false;
        }

        final byte[] imageBytes = imageCache.get(existingInvoice.imageUrl());
        if (imageBytes == null) {
            return false;
        }

        final MimeType mimeType = detectMimeType(imageBytes);
        return taskQueue.offer(() -> reprocessInvoice(key, jsessionId, imageBytes, mimeType));
    }

    private void reprocessInvoice(
            final String key, final String jsessionId, final byte[] imageBytes, final MimeType mimeType) {
        try {
            final Invoice invoice = recognizeInvoice(imageBytes, mimeType);
            final List<String> qrInvoiceNumbers = invoiceQrCodeReader.readInvoiceNumbers(imageBytes);
            final InvoiceResult result = checkInvoiceResult(invoice);
            final UserData userData = userCache.get(jsessionId);
            if (userData == null) {
                return;
            }

            final List<InvoiceDTO> data = userData.getData();
            InvoiceDTO updatedInvoice = null;
            synchronized (data) {
                for (int index = 0; index < data.size(); index++) {
                    final InvoiceDTO existingInvoice = data.get(index);
                    if (existingInvoice.key().equals(key)) {
                        updatedInvoice = new InvoiceDTO(
                                existingInvoice.key(),
                                invoice.invoiceNumber,
                                invoice.invoiceDate,
                                qrInvoiceNumbers,
                                result,
                                existingInvoice.imageUrl());
                        data.set(index, updatedInvoice);
                        break;
                    }
                }
            }
            if (updatedInvoice == null) {
                return;
            }

            final EmitResult emitResult = invoiceChannels.tryEmitNext(jsessionId, updatedInvoice);
            log.info("invoice {} reprocessed; emitResult {}", key, emitResult);
        } catch (Exception e) {
            log.error("invoice {} reprocessing failed", key, e);
        }
    }

    private InvoiceResult checkInvoiceResult(final Invoice invoice) {
        final int separatorIndex = invoice.invoiceNumber.indexOf('-');
        final String invoiceDigits =
                separatorIndex >= 0 ? invoice.invoiceNumber.substring(separatorIndex + 1) : "";
        return invoiceService.checkInvoice(invoice.invoiceDate, invoiceDigits);
    }

    private MimeType detectMimeType(final byte[] imageBytes) {
        try (var input = new ByteArrayInputStream(imageBytes)) {
            final String detectedMimeType = URLConnection.guessContentTypeFromStream(input);
            if (detectedMimeType != null) {
                return MimeTypeUtils.parseMimeType(detectedMimeType);
            }
        } catch (IOException e) {
            log.warn("unable to detect cached invoice image type", e);
        }
        return MimeTypeUtils.parseMimeType("image/png");
    }

    private Invoice recognizeInvoice(final byte[] imageBytes, final MimeType mimeType) {
        try {
            final var paddleXResult = paddleXOcrClient.recognize(imageBytes);
            if (paddleXResult.isPresent()) {
                final var result = paddleXResult.get();
                log.info("invoice OCR completed with PaddleX");
                return new Invoice(result.invoiceNumber(), result.invoiceDate());
            }
            log.warn("PaddleX OCR did not find both the invoice number and date; using AI fallback");
        } catch (Exception e) {
            log.warn("PaddleX OCR unavailable; using AI fallback: {}", e.getMessage());
        }

        final Resource resource = new ByteArrayResource(imageBytes);
        final UserMessage.Builder builder = UserMessage.builder().text(AI_PROMPT);
        builder.media(new Media(mimeType, resource));
        final Invoice invoice = chatClient.prompt(new Prompt(builder.build())).call().entity(Invoice.class);
        return invoice == null ? new Invoice(NA, NA) : invoice;
    }

    public void deleteInvoice(final String key) {
        final VaadinServletRequest vaadinRequest = (VaadinServletRequest) VaadinService.getCurrentRequest();
        final HttpServletRequest request = vaadinRequest.getHttpServletRequest();
        final HttpSession vaadinSession = request.getSession();
        final String id = vaadinSession.getId();
        final var user = userCache.get(id);
        if (user == null) {
            return;
        }
        var data = user.getData();
        data = Collections.synchronizedList(
                new ArrayList<>(data.stream().filter(t -> !t.key().equals(key)).toList()));
        final UserData userData = new UserData();
        userData.setData(data);
        userCache.put(id, userData);
        imageCache.delete(key);
    }

    static class Invoice {
        @NonNull
        String invoiceNumber;

        @NonNull
        String invoiceDate;

        public String getInvoiceNumber() {
            return invoiceNumber;
        }

        public void setInvoiceNumber(final String invoiceNumber) {
            this.invoiceNumber = invoiceNumber.replaceAll("\\s+", "");
        }

        public String getInvoiceDate() {
            return invoiceDate;
        }

        public void setInvoiceDate(final String invoiceDate) {
            this.invoiceDate = invoiceDate.replaceAll("\\s+", "");
            final Pattern pattern = Pattern.compile("\\d{3}年\\d{2}-\\d{2}月");
            final Matcher matcher = pattern.matcher(this.invoiceDate);
            if (matcher.find()) {
                this.invoiceDate = matcher.group();
            }
        }

        public Invoice(@NonNull final String invoiceNumber, @NonNull final String invoiceDate) {
            super();
            this.invoiceNumber = invoiceNumber;
            this.invoiceDate = invoiceDate;
        }

        public Invoice() {
            this.invoiceNumber = NA;
            this.invoiceDate = NA;
        }
    }
    private static byte[] resizePng(byte[] pngBytes, int maxSize) throws IOException {
        BufferedImage original;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(pngBytes)) {
            original = ImageIO.read(bis);
        }

        if (original == null) {
            throw new IllegalArgumentException("Invalid image data");
        }

        int width = original.getWidth();
        int height = original.getHeight();

        // 2. 計算等比例縮放
        float scale = Math.min(
                (float) maxSize / width,
                (float) maxSize / height
        );

        // 如果本來就 <= 300，可以直接回傳
        if (scale >= 1.0f) {
            return pngBytes;
        }

        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        // 3. 建立縮圖（保留透明背景）
        BufferedImage resized = new BufferedImage(
                newWidth,
                newHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        // 4. BufferedImage → byte[]
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(resized, "png", bos);
            return bos.toByteArray();
        }
    }
}
