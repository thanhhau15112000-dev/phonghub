package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.service.MaintenanceSlaService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Mỗi giờ (và lúc khởi động) báo các phiếu bảo trì vừa quá hạn SLA; mỗi phiếu chỉ báo một lần. */
@Component
@ConditionalOnProperty(name = "phonghub.maintenance.sla-notify-enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceSlaScheduler.class);

    private final MaintenanceSlaService slaService;
    private final TransactionTemplate transactionTemplate;

    public MaintenanceSlaScheduler(MaintenanceSlaService slaService, TransactionTemplate transactionTemplate) {
        this.slaService = slaService;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 10 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void hourly() {
        run();
    }

    public int run() {
        try {
            Integer notified = transactionTemplate.execute(status -> slaService.notifyOverdue(Instant.now()));
            if (notified != null && notified > 0) {
                log.info("Đã báo {} phiếu bảo trì quá hạn SLA", notified);
            }
            return notified == null ? 0 : notified;
        } catch (RuntimeException ex) {
            log.error("Job báo phiếu quá hạn SLA bị lỗi", ex);
            return 0;
        }
    }
}
