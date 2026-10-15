package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.port.in.ContractLifecycleUseCase;
import com.phonghub.application.port.in.ContractLifecycleUseCase.RunResult;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Mỗi ngày lúc 00:15 (và khi khởi động) chuyển hợp đồng quá hạn sang EXPIRED và nhắc hợp đồng sắp hết hạn (#87). */
@Component
@ConditionalOnProperty(name = "phonghub.contracts.expiry-job-enabled", havingValue = "true", matchIfMissing = true)
public class ContractExpiryScheduler {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger log = LoggerFactory.getLogger(ContractExpiryScheduler.class);

    private final ContractLifecycleUseCase lifecycle;
    private final TransactionTemplate transactionTemplate;

    public ContractExpiryScheduler(ContractLifecycleUseCase lifecycle, TransactionTemplate transactionTemplate) {
        this.lifecycle = lifecycle;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run(LocalDate.now(ZONE));
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void daily() {
        run(LocalDate.now(ZONE));
    }

    /** Một lần chạy trong một transaction; lỗi được ghi log để lần chạy sau thử lại, không làm sập ứng dụng. */
    public RunResult run(LocalDate today) {
        try {
            RunResult result = transactionTemplate.execute(status -> lifecycle.runDaily(today));
            if (result != null && (result.expired() > 0 || result.reminded() > 0)) {
                log.info("Hợp đồng: {} chuyển hết hạn, {} được nhắc sắp hết hạn", result.expired(), result.reminded());
            }
            return result == null ? new RunResult(0, 0) : result;
        } catch (RuntimeException ex) {
            log.error("Job hết hạn/nhắc hợp đồng bị lỗi", ex);
            return new RunResult(0, 0);
        }
    }
}
