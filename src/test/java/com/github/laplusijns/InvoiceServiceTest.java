package com.github.laplusijns;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC);
    private static final InvoicePeriod PERIOD = new InvoicePeriod(
            "115年05-06月",
            "38548029",
            "10138845",
            List.of("24121106", "28589937", "83663333"),
            List.of("045"));

    @Test
    void checksAllPrizeLevelsUsingTheRssSnapshot() {
        final AtomicInteger fetchCount = new AtomicInteger();
        final var service = new InvoiceService(
                () -> {
                    fetchCount.incrementAndGet();
                    return List.of(PERIOD);
                },
                Duration.ofHours(6),
                Duration.ofMinutes(5),
                CLOCK);

        assertThat(service.invoicePeriods()).containsExactly("115年05-06月");
        assertThat(service.checkInvoice("115年05-06月", "38548029")).isEqualTo(InvoiceResult.SPECIAL_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "10138845")).isEqualTo(InvoiceResult.GRAND_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "24121106")).isEqualTo(InvoiceResult.FIRST_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "04121106")).isEqualTo(InvoiceResult.SECOND_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "00121106")).isEqualTo(InvoiceResult.THIRD_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "00021106")).isEqualTo(InvoiceResult.FOURTH_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "00001106")).isEqualTo(InvoiceResult.FIFTH_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "00000106")).isEqualTo(InvoiceResult.SIXTH_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "12345045")).isEqualTo(InvoiceResult.SIXTH_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "12345678")).isEqualTo(InvoiceResult.NO_PRIZE);
        assertThat(fetchCount).hasValue(1);
    }

    @Test
    void reportsUnavailableInsteadOfNotWinningWhenTheRssCannotBeLoaded() {
        final var service = new InvoiceService(
                () -> {
                    throw new IllegalStateException("network unavailable");
                },
                Duration.ofHours(6),
                Duration.ofMinutes(5),
                CLOCK);

        assertThat(service.invoicePeriods()).isEmpty();
        assertThat(service.checkInvoice("115年05-06月", "12345678"))
                .isEqualTo(InvoiceResult.ERROR_PRIZE_DATA_UNAVAILABLE);
    }

    @Test
    void keepsKnownDataButDoesNotTrustAnUnknownPeriodAfterARefreshFailure() {
        final AtomicInteger fetchCount = new AtomicInteger();
        final var service = new InvoiceService(
                () -> {
                    if (fetchCount.getAndIncrement() == 0) {
                        return List.of(PERIOD);
                    }
                    throw new IllegalStateException("network unavailable");
                },
                Duration.ZERO,
                Duration.ZERO,
                CLOCK);

        assertThat(service.checkInvoice("115年05-06月", "38548029")).isEqualTo(InvoiceResult.SPECIAL_PRIZE);
        assertThat(service.checkInvoice("115年05-06月", "38548029")).isEqualTo(InvoiceResult.SPECIAL_PRIZE);
        assertThat(service.checkInvoice("115年07-08月", "12345678"))
                .isEqualTo(InvoiceResult.ERROR_PRIZE_DATA_UNAVAILABLE);
    }

    @Test
    void reportsAUnknownPeriodAfterASuccessfulRefresh() {
        final var service = new InvoiceService(
                () -> List.of(PERIOD), Duration.ofHours(6), Duration.ofMinutes(5), CLOCK);

        assertThat(service.checkInvoice("115年07-08月", "12345678")).isEqualTo(InvoiceResult.ERROR_NOT_FOUND);
    }

    @Test
    void rejectsANonNumericInvoiceNumberWithoutFetchingTheRss() {
        final AtomicInteger fetchCount = new AtomicInteger();
        final var service = new InvoiceService(
                () -> {
                    fetchCount.incrementAndGet();
                    return List.of(PERIOD);
                },
                Duration.ofHours(6),
                Duration.ofMinutes(5),
                CLOCK);

        assertThat(service.checkInvoice("115年05-06月", "AB123456"))
                .isEqualTo(InvoiceResult.ERROR_EIGHT_NUMBER);
        assertThat(fetchCount).hasValue(0);
    }
}
