package com.github.laplusijns;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class InvoiceQrCodeReaderTest {

    private final InvoiceQrCodeReader reader = new InvoiceQrCodeReader();

    @Test
    void extractsTaiwanInvoiceNumberFromQrPayloadPrefix() {
        assertThat(InvoiceQrCodeReader.extractInvoiceNumber("AB12345678114010112345678"))
                .contains("AB-12345678");
        assertThat(InvoiceQrCodeReader.extractInvoiceNumber("ＣＤ-８７６５４３２１"))
                .contains("CD-87654321");
        assertThat(InvoiceQrCodeReader.extractInvoiceNumber("**right-side-detail"))
                .isEmpty();
    }

    @Test
    void readsInvoiceNumbersFromMultipleQrCodes() throws Exception {
        final byte[] image = createQrImage("AB123456781140101payload", "CD876543211140101payload");

        assertThat(reader.readInvoiceNumbers(image))
                .containsExactlyInAnyOrder("AB-12345678", "CD-87654321");
    }

    @Test
    void returnsEmptyListWhenImageHasNoQrCode() throws IOException {
        final var image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        assertThat(reader.readInvoiceNumbers(toPng(image))).isEmpty();
    }

    private byte[] createQrImage(final String... values) throws WriterException, IOException {
        final int qrSize = 240;
        final int gap = 40;
        final int imageWidth = values.length * qrSize + (values.length + 1) * gap;
        final int imageHeight = qrSize + gap * 2;
        final var image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, imageWidth, imageHeight);

        final var writer = new QRCodeWriter();
        for (int index = 0; index < values.length; index++) {
            final BitMatrix matrix = writer.encode(values[index], BarcodeFormat.QR_CODE, qrSize, qrSize);
            final int offsetX = gap + index * (qrSize + gap);
            for (int x = 0; x < qrSize; x++) {
                for (int y = 0; y < qrSize; y++) {
                    image.setRGB(
                            offsetX + x,
                            gap + y,
                            matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
        }
        graphics.dispose();
        return toPng(image);
    }

    private byte[] toPng(final BufferedImage image) throws IOException {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
