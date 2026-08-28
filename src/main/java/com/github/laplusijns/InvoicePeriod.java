package com.github.laplusijns;

import java.util.List;
import java.util.regex.Pattern;

record InvoicePeriod(
        String period,
        String specialPrize,
        String grandPrize,
        List<String> firstPrizes,
        List<String> additionalSixthPrizes) {

    private static final Pattern PERIOD = Pattern.compile("\\d{2,3}年\\d{2}-\\d{2}月");
    private static final Pattern EIGHT_DIGITS = Pattern.compile("\\d{8}");
    private static final Pattern THREE_DIGITS = Pattern.compile("\\d{3}");

    InvoicePeriod {
        if (!PERIOD.matcher(period).matches()) {
            throw new IllegalArgumentException("Invalid invoice period: " + period);
        }
        if (!EIGHT_DIGITS.matcher(specialPrize).matches()) {
            throw new IllegalArgumentException("Invalid special prize number");
        }
        if (!EIGHT_DIGITS.matcher(grandPrize).matches()) {
            throw new IllegalArgumentException("Invalid grand prize number");
        }
        firstPrizes = List.copyOf(firstPrizes);
        if (firstPrizes.size() != 3 || firstPrizes.stream().anyMatch(number -> !EIGHT_DIGITS.matcher(number).matches())) {
            throw new IllegalArgumentException("Exactly three valid first prize numbers are required");
        }
        additionalSixthPrizes = List.copyOf(additionalSixthPrizes);
        if (additionalSixthPrizes.stream()
                .anyMatch(number -> !THREE_DIGITS.matcher(number).matches())) {
            throw new IllegalArgumentException("Invalid additional sixth prize number");
        }
    }
}
