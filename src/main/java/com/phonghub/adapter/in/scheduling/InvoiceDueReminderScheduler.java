package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.port.in.InvoiceDueReminderUseCase;
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

/** Mỗi ngày lúc 07:00 (giờ Việt Nam) và khi khởi động, nhắc người thuê hóa đơn sắp đến hạn (#86). */
@Component
@ConditionalOnProperty(name = "phonghub.invoicing.due-reminder-enabled", havingValue = "true", matchIfMissing = true)
public class InvoiceDueReminderScheduler {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Logger log = LoggerFactory.getLogger(InvoiceDueReminderScheduler.class);

    private final InvoiceDueReminderUseCase reminders;
    private final TransactionTemplate transactionTemplate;

    public InvoiceDueReminderScheduler(InvoiceDueReminderUseCase reminders, TransactionTemplate transactionTemplate) {
        this.reminders = reminders;
        this.transactionTemplate = transactionTemplate;
    }

    /** Khởi động lại sau 07:00 vẫn nhắc hóa đơn đến hạn hôm nay; chạy lại không nhắc trùng. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run(LocalDate.now(ZONE));
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void daily() {
        run(LocalDate.now(ZONE));
    }

    /** Một lần chạy trong một transaction; lỗi được ghi log để lần chạy sau thử lại, không làm sập ứng dụng. */
    public int run(LocalDate today) {
        try {
            Integer reminded = transactionTemplate.execute(status -> reminders.runDaily(today));
            if (reminded != null && reminded > 0) {
                log.info("Đã nhắc {} hóa đơn sắp đến hạn", reminded);
            }
            return reminded == null ? 0 : reminded;
        } catch (RuntimeException ex) {
            log.error("Job nhắc hóa đơn sắp đến hạn bị lỗi", ex);
            return 0;
        }
    }
}
