package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.port.in.MonthlyInvoiceUseCase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tự động tạo hóa đơn tháng cho người thuê.
 *
 * <ul>
 *   <li>Ngày cuối tháng (giờ Việt Nam): tạo hóa đơn tháng sau, gộp phí sửa chữa chưa trả.</li>
 *   <li>Chạy bù mỗi giờ và lúc khởi động: hợp đồng nào thiếu hóa đơn tháng hiện tại thì tạo, vì instance
 *       Render bản miễn phí ngủ khi không có truy cập nên một lần chạy cố định có thể bị lỡ.</li>
 * </ul>
 * Chỉ tạo hóa đơn còn thiếu nên chạy lặp lại không tạo trùng.
 */
@Component
@ConditionalOnProperty(name = "phonghub.invoicing.auto-generate", havingValue = "true", matchIfMissing = true)
public class MonthlyInvoiceScheduler {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger log = LoggerFactory.getLogger(MonthlyInvoiceScheduler.class);

    private final MonthlyInvoiceUseCase monthlyInvoiceUseCase;

    public MonthlyInvoiceScheduler(MonthlyInvoiceUseCase monthlyInvoiceUseCase) {
        this.monthlyInvoiceUseCase = monthlyInvoiceUseCase;
    }

    /** Các tháng cần bảo đảm có hóa đơn vào ngày {@code today}: tháng hiện tại, và tháng sau nếu hôm nay là ngày cuối tháng. */
    public static List<YearMonth> periodsToEnsure(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        List<YearMonth> periods = new ArrayList<>();
        periods.add(current);
        if (today.equals(current.atEndOfMonth())) {
            periods.add(current.plusMonths(1));
        }
        return periods;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run(LocalDate.now(ZONE));
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void hourly() {
        run(LocalDate.now(ZONE));
    }

    /** Trả về số hóa đơn đã tạo. Lỗi của một hợp đồng được ghi log và không chặn các hợp đồng còn lại. */
    public int run(LocalDate today) {
        int created = 0;
        for (YearMonth period : periodsToEnsure(today)) {
            List<UUID> contractIds;
            try {
                contractIds = monthlyInvoiceUseCase.findContractsNeedingInvoice(period);
            } catch (Exception ex) {
                log.error("Không lấy được danh sách hợp đồng cần hóa đơn tháng {}", period, ex);
                continue;
            }
            for (UUID contractId : contractIds) {
                try {
                    if (monthlyInvoiceUseCase.issueForContract(contractId, period)) {
                        created++;
                        log.info("Đã tự động tạo hóa đơn tháng {} cho hợp đồng {}", period, contractId);
                    }
                } catch (Exception ex) {
                    log.error("Không tạo được hóa đơn tháng {} cho hợp đồng {}", period, contractId, ex);
                }
            }
        }
        return created;
    }
}
