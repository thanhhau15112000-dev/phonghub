package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.BillingCycle;

import java.time.LocalDate;
import java.util.List;

/** Chọn kỳ thanh toán để tô và đặt làm tâm khi thu gọn bảng chu kỳ (#363). */
final class BillingCycleDisplay {

    private BillingCycleDisplay() {
    }

    /**
     * Kỳ làm tâm khi thu gọn bảng: kỳ chứa {@code today}; chưa tới kỳ đầu thì là kỳ đầu, đã qua kỳ cuối thì là kỳ
     * cuối; không có kỳ nào thì 0.
     */
    static int focusNumber(List<BillingCycle> cycles, LocalDate today) {
        if (cycles == null || cycles.isEmpty()) {
            return 0;
        }
        int running = runningNumber(cycles, today);
        if (running > 0) {
            return running;
        }
        BillingCycle first = cycles.get(0);
        if (today.isBefore(first.startDate())) {
            return first.number();
        }
        return cycles.get(cycles.size() - 1).number();
    }

    /** Kỳ đang diễn ra (chứa {@code today}, tính cả ngày đầu và ngày cuối kỳ); ngoài thời hạn hợp đồng thì 0. */
    static int runningNumber(List<BillingCycle> cycles, LocalDate today) {
        if (cycles == null) {
            return 0;
        }
        for (BillingCycle cycle : cycles) {
            if (!today.isBefore(cycle.startDate()) && !today.isAfter(cycle.endDate())) {
                return cycle.number();
            }
        }
        return 0;
    }
}
