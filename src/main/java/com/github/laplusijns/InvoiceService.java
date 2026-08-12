package com.github.laplusijns;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private static final Map<String, InvoicePeriod> PERIOD_MAP = new HashMap<>();

    static {
        // 114年09~10月
        PERIOD_MAP.put(
                "114年09-10月",
                new InvoicePeriod(
                        "114年09-10月", "25834483", "46587380", Arrays.asList("41016094", "98081574", "07309261")));

        // 114年11~12月（⚠️ 之後開獎再填）
        PERIOD_MAP.put(
                "114年11-12月",
                new InvoicePeriod(
                        "114年11-12月", "97023797", "00507588", Arrays.asList("92377231", "05232592", "78125249")));
        // 115年01~02月
        PERIOD_MAP.put(
                "115年01-02月",
                new InvoicePeriod(
                        "115年01-02月", "87510041", "32220522", Arrays.asList("21677046", "44662410", "31262513")));
        PERIOD_MAP.put(
                "115年03-04月",
                new InvoicePeriod(
                        "115年03-04月", "44140251", "14715309", Arrays.asList("86562747", "79171152", "77925523")));
        PERIOD_MAP.put(
                "115年05-06月",
                new InvoicePeriod(
                        "115年05-06月", "38548029", "10138845", Arrays.asList("24121106", "28589937", "83663333")));
    }

    public Set<String> invoicePeriods() {
        return PERIOD_MAP.keySet();
    }

    public InvoiceResult checkInvoice(@NonNull final String periodKey, @NonNull final String invoice) {

        final InvoicePeriod period = PERIOD_MAP.get(periodKey);
        if (period == null) {
            return InvoiceResult.ERROR_NOT_FOUND;
        }

        if (invoice.length() != 8) {
            return InvoiceResult.ERROR_EIGHT_NUMBER;
        }

        // 特別獎
        if (invoice.equals(period.specialPrize)) {
            return InvoiceResult.SPECIAL_PRIZE;
        }

        // 特獎
        if (invoice.equals(period.grandPrize)) {
            return InvoiceResult.GRAND_PRIZE;
        }

        // 頭獎
        for (String first : period.firstPrizes) {
            if (invoice.equals(first)) {
                return InvoiceResult.FIRST_PRIZE;
            }
        }

        // 二～六獎
        for (String first : period.firstPrizes) {
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

        return InvoiceResult.NO_PRIZE;
    }

    public static class InvoicePeriod {

        String period; // 期別
        String specialPrize; // 特別獎
        String grandPrize; // 特獎
        List<String> firstPrizes; // 頭獎

        public InvoicePeriod(
                final String period,
                final String specialPrize,
                final String grandPrize,
                final List<String> firstPrizes) {
            this.period = period;
            this.specialPrize = specialPrize;
            this.grandPrize = grandPrize;
            this.firstPrizes = firstPrizes;
        }
    }
}
