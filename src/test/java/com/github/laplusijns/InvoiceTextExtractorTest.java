package com.github.laplusijns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTextExtractorTest {

    private final InvoiceTextExtractor extractor = new InvoiceTextExtractor();

    @Test
    void extractsInvoiceNumberAndPrintedPeriod() {
        final var result = extractor.extract(List.of("電子發票證明聯", "AB-12345678", "114年09-10月"));

        assertThat(result).contains(new InvoiceOcrResult("AB-12345678", "114年09-10月"));
    }

    @Test
    void normalizesFullWidthAndSplitInvoiceNumber() {
        final var result = extractor.extract(List.of("ＡＢ", "１２３４５６７８", "民國１１４年１２月３日"));

        assertThat(result).contains(new InvoiceOcrResult("AB-12345678", "114年11-12月"));
    }

    @Test
    void convertsGregorianDateToRocInvoicePeriod() {
        final var result = extractor.extract(List.of("CD–87654321", "Date 2025/04/09"));

        assertThat(result).contains(new InvoiceOcrResult("CD-87654321", "114年03-04月"));
    }

    @Test
    void returnsEmptyWhenARequiredFieldIsMissing() {
        assertThat(extractor.extract(List.of("AB-12345678", "未辨識到日期"))).isEmpty();
    }
}
