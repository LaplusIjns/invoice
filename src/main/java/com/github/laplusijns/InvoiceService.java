package com.github.laplusijns;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoicePeriodSource periodSource;
    private final Duration refreshInterval;
    private final Duration failureRetryInterval;
    private final Clock clock;
    private final Object refreshMonitor = new Object();
    private volatile PrizeSnapshot snapshot = new PrizeSnapshot(Map.of(), Instant.EPOCH, false);

    public InvoiceService(
            final InvoicePeriodSource periodSource,
            @Value("${invoice.prizes.refresh-interval:6h}") final Duration refreshInterval,
            @Value("${invoice.prizes.failure-retry-interval:5m}") final Duration failureRetryInterval) {
        this(periodSource, refreshInterval, failureRetryInterval, Clock.systemUTC());
    }

    InvoiceService(
            final InvoicePeriodSource periodSource,
            final Duration refreshInterval,
            final Duration failureRetryInterval,
            final Clock clock) {
        if (refreshInterval.isNegative() || failureRetryInterval.isNegative()) {
            throw new IllegalArgumentException("Invoice prize refresh intervals cannot be negative");
        }
        this.periodSource = periodSource;
        this.refreshInterval = refreshInterval;
        this.failureRetryInterval = failureRetryInterval;
        this.clock = clock;
    }

    public List<String> invoicePeriods() {
        return List.copyOf(refreshIfNeeded().periods().keySet());
    }

    public InvoiceResult checkInvoice(@NonNull final String periodKey, @NonNull final String invoice) {
        if (!invoice.matches("\\d{8}")) {
            return InvoiceResult.ERROR_EIGHT_NUMBER;
        }

        final PrizeSnapshot current = refreshIfNeeded();
        final InvoicePeriod period = current.periods().get(periodKey);
        if (period == null) {
            return current.lastRefreshSucceeded()
                    ? InvoiceResult.ERROR_NOT_FOUND
                    : InvoiceResult.ERROR_PRIZE_DATA_UNAVAILABLE;
        }

        if (invoice.equals(period.specialPrize())) {
            return InvoiceResult.SPECIAL_PRIZE;
        }
        if (invoice.equals(period.grandPrize())) {
            return InvoiceResult.GRAND_PRIZE;
        }

        for (String first : period.firstPrizes()) {
            if (invoice.equals(first)) {
                return InvoiceResult.FIRST_PRIZE;
            }
        }

        for (String first : period.firstPrizes()) {
            if (invoice.endsWith(first.substring(1))) {
                return InvoiceResult.SECOND_PRIZE;
            }
            if (invoice.endsWith(first.substring(2))) {
                return InvoiceResult.THIRD_PRIZE;
            }
            if (invoice.endsWith(first.substring(3))) {
                return InvoiceResult.FOURTH_PRIZE;
            }
            if (invoice.endsWith(first.substring(4))) {
                return InvoiceResult.FIFTH_PRIZE;
            }
            if (invoice.endsWith(first.substring(5))) {
                return InvoiceResult.SIXTH_PRIZE;
            }
        }

        if (period.additionalSixthPrizes().stream().anyMatch(invoice::endsWith)) {
            return InvoiceResult.SIXTH_PRIZE;
        }
        return InvoiceResult.NO_PRIZE;
    }

    private PrizeSnapshot refreshIfNeeded() {
        final Instant now = clock.instant();
        PrizeSnapshot current = snapshot;
        if (now.isBefore(current.refreshAfter())) {
            return current;
        }

        synchronized (refreshMonitor) {
            current = snapshot;
            if (now.isBefore(current.refreshAfter())) {
                return current;
            }
            try {
                final List<InvoicePeriod> fetched = periodSource.fetchPeriods();
                if (fetched.isEmpty()) {
                    throw new IllegalStateException("Invoice prize source returned no periods");
                }
                final Map<String, InvoicePeriod> periods = new LinkedHashMap<>();
                for (InvoicePeriod period : fetched) {
                    if (periods.putIfAbsent(period.period(), period) != null) {
                        throw new IllegalStateException("Duplicate invoice period: " + period.period());
                    }
                }
                final Map<String, InvoicePeriod> immutablePeriods =
                        Collections.unmodifiableMap(new LinkedHashMap<>(periods));
                current = new PrizeSnapshot(immutablePeriods, now.plus(refreshInterval), true);
                snapshot = current;
                log.info("Loaded {} invoice prize periods from RSS", periods.size());
            } catch (RuntimeException e) {
                current = new PrizeSnapshot(current.periods(), now.plus(failureRetryInterval), false);
                snapshot = current;
                log.warn("Unable to refresh invoice prize RSS; keeping the last valid snapshot: {}", e.getMessage());
                log.debug("Invoice prize RSS refresh failure", e);
            }
            return current;
        }
    }

    private record PrizeSnapshot(
            Map<String, InvoicePeriod> periods, Instant refreshAfter, boolean lastRefreshSucceeded) {}
}
