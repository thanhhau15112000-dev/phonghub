package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.port.in.LateFeeAssessmentUseCase;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tính phạt trả trễ cho hóa đơn tiền thuê quá hạn (#80). Chạy mỗi giờ và lúc khởi động (instance Render bản
 * miễn phí ngủ khi không có truy cập nên một lần chạy cố định có thể bị lỡ). Phạt được đặt chứ không cộng dồn
 * nên chạy lặp lại không tính thêm.
 */
@Component
@ConditionalOnProperty(name = "phonghub.invoicing.auto-generate", havingValue = "true", matchIfMissing = true)
public class LateFeeScheduler {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger log = LoggerFactory.getLogger(LateFeeScheduler.class);

    private final LateFeeAssessmentUseCase assessment;
    private final MeterRegistry meterRegistry;

    public LateFeeScheduler(LateFeeAssessmentUseCase assessment) {
        this(assessment, (ObjectProvider<MeterRegistry>) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LateFeeScheduler(LateFeeAssessmentUseCase assessment, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.assessment = assessment;
        this.meterRegistry = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run(LocalDate.now(ZONE));
    }

    @Scheduled(cron = "0 25 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void hourly() {
        run(LocalDate.now(ZONE));
    }

    /** Trả về số hóa đơn được thêm hoặc cập nhật phạt. Lỗi của một hóa đơn không chặn các hóa đơn còn lại. */
    public int run(LocalDate today) {
        List<UUID> invoiceIds;
        try {
            invoiceIds = assessment.findInvoicesToAssess(today);
        } catch (Exception ex) {
            log.error("Không lấy được danh sách hóa đơn quá hạn để tính phạt", ex);
            recordExecution("error");
            return 0;
        }
        int changed = 0;
        boolean hasError = false;
        for (UUID invoiceId : invoiceIds) {
            try {
                if (assessment.assessInvoice(invoiceId, today)) {
                    changed++;
                    log.info("Đã tính phạt trả trễ cho hóa đơn {}", invoiceId);
                }
            } catch (Exception ex) {
                log.error("Không tính được phạt trả trễ cho hóa đơn {}", invoiceId, ex);
                hasError = true;
            }
        }
        recordExecution(hasError ? "error" : "success");
        return changed;
    }

    private void recordExecution(String status) {
        if (meterRegistry != null) {
            meterRegistry.counter("phonghub.scheduled.job.executions", "job", "late_fee", "status", status).increment();
        }
    }
}
