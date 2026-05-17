package com.github.laplusijns;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.Endpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks.EmitResult;

@Endpoint
@AnonymousAllowed
public class ProcessService {

    ChatClient chatClient;
    InvoiceService invoiceService;
    ImageCache imageCache;
    InvoiceChannels invoiceChannels;
    RetryTemplate retryTemplate;
    UserCache userCache;
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();

    private static final String AI_PROMPT = """
			你是一個專業的 OCR 與台灣發票分析 AI。
			任務：分析我提供的台灣發票圖片，準確抽取以下資訊：
			發票號碼（invoiceNumber）
			格式：兩個英文大寫字母 + 減號 + 八位數字，例如 "AB-11223344"
			發票日期（invoiceDate）
			格式：台灣民國年 + 月份區間，例如 "104年05-06月"
			請以 JSON 格式回傳結果，結構如下：
			{
			  "invoiceNumber": "抽取到的發票號碼",
			  "invoiceDate": "抽取到的發票日期"
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
            final UserCache userCache) {
        super();
        this.chatClient = chatClient;
        this.invoiceService = invoiceService;
        this.imageCache = imageCache;
        this.invoiceChannels = invoiceChannels;
        this.userCache = userCache;

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

    public @NonNull Set<@NonNull String> invoicePeriods() {
        return invoiceService.invoicePeriods();
    }

    public void process2(
            @NonNull final String periodKey, @NonNull final String invoiceNum, @NonNull final String jsessionId) {
        final var userData = userCache.get(jsessionId);
        if (userData == null) {
            log.info("沒有使用者");
            invoiceChannels.createChannel(jsessionId);
            userCache.put(jsessionId, new UserData());
        }
        final List<InvoiceDTO> data =
                userData == null ? userCache.get(jsessionId).getData() : userData.getData();
        log.info("task start {}", new Date());
        final Invoice invoice = new Invoice(invoiceNum, periodKey);

        final InvoiceResult result = invoiceService.checkInvoice(invoice.invoiceDate, invoice.invoiceNumber);
        final String uuid = UUID.randomUUID().toString();
        final String key = UUID.randomUUID().toString();

        final InvoiceDTO invoiceDTO = new InvoiceDTO(key, invoice.invoiceNumber, invoice.invoiceDate, result, uuid);
        data.add(invoiceDTO);
        log.info("{}", jsessionId);
        final EmitResult emitResult = invoiceChannels.tryEmitNext(jsessionId, invoiceDTO);
        log.info("emitResult {}", emitResult);
    }

    public void process(@NonNull final String base64Image, @NonNull final String jsessionId) {

        final var userData = userCache.get(jsessionId);
        if (userData == null) {
            log.info("沒有使用者");
            invoiceChannels.createChannel(jsessionId);
            userCache.put(jsessionId, new UserData());
        }

        final List<InvoiceDTO> data =
                userData == null ? userCache.get(jsessionId).getData() : userData.getData();

        log.info("task start {}", new Date());
        // 把 OCR 任務加入隊列，不直接執行
        final boolean bool = taskQueue.offer(() -> {
        	final String[] parts = base64Image.split(";base64,");
        	final String uuid = UUID.randomUUID().toString();
        	final byte[] imageBytes = Base64.getDecoder().decode(parts[1]);
        	byte[] resizeBytes = new byte[] {};
        	try {
				resizeBytes = resizePng(imageBytes, 300);
			} catch (IOException e) {
				log.error("resizePng exception",e);
			}
            imageCache.put(uuid, imageBytes);
            imageCache.putThumbnail(uuid, resizeBytes);
            final String key = UUID.randomUUID().toString();
            
            try {
                final String mimeTypeString = parts[0].replace("data:", "");
                final Resource resource = new ByteArrayResource(imageBytes);
                final MimeType mimeType = MimeTypeUtils.parseMimeType(mimeTypeString);

                final UserMessage.Builder builder = UserMessage.builder().text(AI_PROMPT);
                builder.media(new Media(mimeType, resource));

                Invoice invoice =
                        chatClient.prompt(new Prompt(builder.build())).call().entity(Invoice.class);
                if (invoice == null) {
                    invoice = new Invoice(NA, NA);
                }
                InvoiceResult result;
                if (NA.equals(invoice.invoiceDate) || NA.equals(invoice.invoiceNumber)) {
                    result = InvoiceResult.ERROR_NOT_FOUND;
                } else {
                    result = invoiceService.checkInvoice(
                            invoice.invoiceDate, invoice.invoiceNumber.split("-")[1]);
                }

                final InvoiceDTO invoiceDTO =
                        new InvoiceDTO(key, invoice.invoiceNumber, invoice.invoiceDate, result, uuid);
                data.add(invoiceDTO);
                log.info("{}", jsessionId);
                final EmitResult emitResult = invoiceChannels.tryEmitNext(jsessionId, invoiceDTO);
                log.info("emitResult {}", emitResult);

            } catch (Exception e) {
            	log.error("taskQueue error",e);
            	final InvoiceDTO invoiceDTO = new InvoiceDTO(key, e.getClass().toString(), "", InvoiceResult.ERROR_NOT_FOUND, uuid);
            	final EmitResult emitResult = invoiceChannels.tryEmitNext(jsessionId, invoiceDTO);
                log.info("exception emitResult {}", emitResult);
            }
        });

        if (!bool) {
            log.error("序列出錯");
        }
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

    private static byte[] resizePng(final byte[] pngBytes, final int maxSize) throws IOException {
        BufferedImage original;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(pngBytes)) {
            original = ImageIO.read(bis);
        }

        if (original == null) {
            throw new IllegalArgumentException("Invalid image data");
        }

        final int width = original.getWidth();
        final int height = original.getHeight();

        // 2. 計算等比例縮放
        final float scale = Math.min((float) maxSize / width, (float) maxSize / height);

        // 如果本來就 <= 300，可以直接回傳
        if (scale >= 1.0f) {
            return pngBytes;
        }

        final int newWidth = Math.round(width * scale);
        final int newHeight = Math.round(height * scale);

        // 3. 建立縮圖（保留透明背景）
        final BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        final Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        // 4. BufferedImage → byte[]
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(resized, "png", bos);
            return bos.toByteArray();
        }
    }
}
