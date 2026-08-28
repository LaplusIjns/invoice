package com.github.laplusijns;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvoiceProcessingStatusTest {

    @Test
    void exposesTheUserFacingProcessingStates() {
        assertThat(InvoiceProcessingStatus.values())
                .extracting(InvoiceProcessingStatus::getValue)
                .containsExactly("排隊中", "辨識中", "失敗", "待確認");
    }
}
