package com.github.laplusijns;

import java.io.Serializable;
import java.util.List;
import org.jspecify.annotations.NonNull;

public record InvoiceDTO(
        @NonNull String key,
        @NonNull String invoiceNumber,
        @NonNull String invoiceDate,
        @NonNull List<@NonNull String> qrInvoiceNumbers,
        @NonNull InvoiceResult result,
        @NonNull String imageUrl)
        implements Serializable {}
