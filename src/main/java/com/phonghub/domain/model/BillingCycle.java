package com.phonghub.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;

/** One rent cycle anchored to the contract activation date. */
public record BillingCycle(int number, LocalDate startDate, LocalDate endDate) {

    public BillingCycle {
        if (number < 1 || startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Kỳ thanh toán không hợp lệ.");
        }
    }

    public YearMonth period() {
        return YearMonth.from(startDate);
    }
}
