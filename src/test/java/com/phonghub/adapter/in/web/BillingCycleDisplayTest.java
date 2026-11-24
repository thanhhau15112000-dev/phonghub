package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.BillingCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillingCycleDisplayTest {

    private static final List<BillingCycle> CYCLES = List.of(
        new BillingCycle(1, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)),
        new BillingCycle(2, LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 24)),
        new BillingCycle(3, LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 24)));

    @Test
    @DisplayName("Kỳ đang diễn ra là kỳ chứa ngày hôm nay, kể cả ngày đầu và ngày cuối kỳ (#363)")
    void cycleContainingToday() {
        assertThat(BillingCycleDisplay.runningNumber(CYCLES, LocalDate.of(2026, 10, 25))).isEqualTo(1);
        assertThat(BillingCycleDisplay.runningNumber(CYCLES, LocalDate.of(2026, 11, 24))).isEqualTo(1);
        assertThat(BillingCycleDisplay.runningNumber(CYCLES, LocalDate.of(2026, 11, 25))).isEqualTo(2);
        assertThat(BillingCycleDisplay.runningNumber(CYCLES, LocalDate.of(2027, 1, 24))).isEqualTo(3);
        assertThat(BillingCycleDisplay.focusNumber(CYCLES, LocalDate.of(2026, 11, 25))).isEqualTo(2);
    }

    @Test
    @DisplayName("Ngoài thời hạn hợp đồng: không có kỳ đang diễn ra, bảng thu gọn quanh kỳ đầu hoặc kỳ cuối")
    void outsideContract() {
        LocalDate beforeStart = LocalDate.of(2026, 10, 1);
        LocalDate afterEnd = LocalDate.of(2027, 3, 1);
        assertThat(BillingCycleDisplay.runningNumber(CYCLES, beforeStart)).isZero();
        assertThat(BillingCycleDisplay.runningNumber(CYCLES, afterEnd)).isZero();
        assertThat(BillingCycleDisplay.focusNumber(CYCLES, beforeStart)).isEqualTo(1);
        assertThat(BillingCycleDisplay.focusNumber(CYCLES, afterEnd)).isEqualTo(3);
        assertThat(BillingCycleDisplay.focusNumber(List.of(), afterEnd)).isZero();
        assertThat(BillingCycleDisplay.runningNumber(List.of(), afterEnd)).isZero();
    }
}
