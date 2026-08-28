package com.github.laplusijns;

import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;

public enum InvoiceProcessingStatus implements Serializable {
    QUEUED("排隊中"),
    RECOGNIZING("辨識中"),
    FAILED("失敗"),
    PENDING_CONFIRMATION("待確認");

    private final String value;

    InvoiceProcessingStatus(final String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
