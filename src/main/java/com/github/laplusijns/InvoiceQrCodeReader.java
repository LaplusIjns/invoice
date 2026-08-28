package com.github.laplusijns;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class InvoiceQrCodeReader {

    private static final Logger log = LoggerFactory.getLogger(InvoiceQrCodeReader.class);
    private static final Pattern INVOICE_NUMBER_PREFIX = Pattern.compile("^\\s*([A-Z]{2})-?([0-9]{8})");
    private static final Map<DecodeHintType, Object> DECODE_HINTS = Map.of(DecodeHintType.TRY_HARDER, true);

    List<String> readInvoiceNumbers(final byte[] imageBytes) {
        final BufferedImage image;
        try (var input = new ByteArrayInputStream(imageBytes)) {
            image = ImageIO.read(input);
        } catch (IOException e) {
            log.warn("unable to read image while scanning QR codes", e);
            return List.of();
        }
        if (image == null) {
            return List.of();
        }

        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        final var source = new RGBLuminanceSource(width, height, pixels);
        final var bitmap = new BinaryBitmap(new HybridBinarizer(source));

        final Result[] qrCodes;
        try {
            qrCodes = new QRCodeMultiReader().decodeMultiple(bitmap, DECODE_HINTS);
        } catch (NotFoundException e) {
            return List.of();
        }

        final var invoiceNumbers = new LinkedHashSet<String>();
        Arrays.stream(qrCodes)
                .map(Result::getText)
                .map(InvoiceQrCodeReader::extractInvoiceNumber)
                .flatMap(Optional::stream)
                .forEach(invoiceNumbers::add);
        return List.copyOf(invoiceNumbers);
    }

    static Optional<String> extractInvoiceNumber(final String qrText) {
        if (qrText == null || qrText.isBlank()) {
            return Optional.empty();
        }
        final String normalized = Normalizer.normalize(qrText, Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT);
        final Matcher matcher = INVOICE_NUMBER_PREFIX.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) + "-" + matcher.group(2));
    }
}
