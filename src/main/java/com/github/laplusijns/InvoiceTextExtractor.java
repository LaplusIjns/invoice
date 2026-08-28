package com.github.laplusijns;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class InvoiceTextExtractor {

    private static final Pattern INVOICE_NUMBER = Pattern.compile(
            "(?<![A-Z0-9])([A-Z]{2})\\s*[-‐‑‒–—]?\\s*((?:\\d\\s*){8})(?!\\d)");
    private static final Pattern PERIOD = Pattern.compile(
            "(?<!\\d)(\\d{2,3})\\s*年\\s*(\\d{1,2})\\s*[-~～至到]\\s*(\\d{1,2})\\s*月");
    private static final Pattern CHINESE_DATE =
            Pattern.compile("(?<!\\d)(\\d{2,4})\\s*年\\s*(\\d{1,2})\\s*月");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(\\d{3,4})\\s*[/.-]\\s*(\\d{1,2})\\s*[/.-]\\s*\\d{1,2}(?!\\d)");

    Optional<InvoiceOcrResult> extract(final List<String> recognizedTexts) {
        final String text = Normalizer.normalize(
                        recognizedTexts.stream()
                                .filter(value -> value != null && !value.isBlank())
                                .collect(Collectors.joining("\n")),
                        Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT);

        final Optional<String> invoiceNumber = findInvoiceNumber(text);
        final Optional<String> invoicePeriod = findInvoicePeriod(text);
        if (invoiceNumber.isEmpty() || invoicePeriod.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new InvoiceOcrResult(invoiceNumber.get(), invoicePeriod.get()));
    }

    private Optional<String> findInvoiceNumber(final String text) {
        final Matcher matcher = INVOICE_NUMBER.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) + "-" + matcher.group(2).replaceAll("\\s+", ""));
    }

    private Optional<String> findInvoicePeriod(final String text) {
        final Matcher periodMatcher = PERIOD.matcher(text);
        while (periodMatcher.find()) {
            final int year = Integer.parseInt(periodMatcher.group(1));
            final int startMonth = Integer.parseInt(periodMatcher.group(2));
            final int endMonth = Integer.parseInt(periodMatcher.group(3));
            if (isValidYear(year) && startMonth >= 1 && startMonth <= 11 && startMonth % 2 == 1
                    && endMonth == startMonth + 1) {
                return Optional.of(formatPeriod(year, startMonth));
            }
        }

        final Optional<String> chineseDatePeriod = findPeriodFromDate(CHINESE_DATE.matcher(text));
        if (chineseDatePeriod.isPresent()) {
            return chineseDatePeriod;
        }
        return findPeriodFromDate(NUMERIC_DATE.matcher(text));
    }

    private Optional<String> findPeriodFromDate(final Matcher matcher) {
        while (matcher.find()) {
            final int year = toRocYear(Integer.parseInt(matcher.group(1)));
            final int month = Integer.parseInt(matcher.group(2));
            if (isValidYear(year) && month >= 1 && month <= 12) {
                final int startMonth = month % 2 == 0 ? month - 1 : month;
                return Optional.of(formatPeriod(year, startMonth));
            }
        }
        return Optional.empty();
    }

    private int toRocYear(final int year) {
        return year >= 1912 ? year - 1911 : year;
    }

    private boolean isValidYear(final int year) {
        return year > 0 && year <= 999;
    }

    private String formatPeriod(final int year, final int startMonth) {
        return "%d年%02d-%02d月".formatted(year, startMonth, startMonth + 1);
    }
}
