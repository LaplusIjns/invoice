package com.github.laplusijns;

import java.util.List;

@FunctionalInterface
interface InvoicePeriodSource {

    List<InvoicePeriod> fetchPeriods();
}
