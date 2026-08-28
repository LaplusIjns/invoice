package com.github.laplusijns;

import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;

public enum InvoiceResult implements Serializable {
    SPECIAL_PRIZE("特別獎"),
    GRAND_PRIZE("特獎"),
    FIRST_PRIZE("頭獎"),
    SECOND_PRIZE("二獎"),
    THIRD_PRIZE("三獎"),
    FOURTH_PRIZE("四獎"),
    FIFTH_PRIZE("五獎"),
    SIXTH_PRIZE("六獎"),
    ERROR_NOT_FOUND("尚未開獎或期別不存在"),
    ERROR_PRIZE_DATA_UNAVAILABLE("中獎資料暫時無法取得"),
    ERROR_EIGHT_NUMBER("號碼不對"),
    ERROR_RECOGNITION("辨識失敗"),
    NO_PRIZE("未中獎"),
    PROGRESS("處理中");

    private final String value;

    InvoiceResult(final String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
