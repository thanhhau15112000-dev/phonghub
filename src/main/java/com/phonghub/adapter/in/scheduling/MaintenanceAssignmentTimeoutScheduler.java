package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.service.MaintenanceAssignmentTimeoutService;
import com.phonghub.application.service.MaintenanceAssignmentTimeoutService.ProcessResult;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Kiểm tra thời hạn giao kỹ thuật viên mỗi giờ và lúc khởi động; mỗi phiếu được xử lý trong một transaction riêng. */
@Component
public class MaintenanceAssignmentTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceAssignmentTimeoutScheduler.class);

    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final MaintenanceAssignmentTimeoutService timeoutService;
    private final TransactionTemplate transactionTemplate;

    public MaintenanceAssignmentTimeoutScheduler(
        MaintenanceTicketRepositoryPort ticketRepository,
        MaintenanceAssignmentTimeoutService timeoutService,
        TransactionTemplate transactionTemplate
    ) {
        this.ticketRepository = ticketRepository;
        this.timeoutService = timeoutService;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 30 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void hourly() {
        run();
    }

    public int run() {
        Instant now = Instant.now();
        int processed = 0;
        List<MaintenanceTicket> tickets;
        try {
            tickets = ticketRepository.findAll();
        } catch (RuntimeException ex) {
            log.error("Không tải được danh sách phiếu bảo trì để kiểm tra thời hạn giao việc", ex);
            return 0;
        }
        for (var ticket : tickets) {
            try {
                ProcessResult result = transactionTemplate.execute(
                    status -> timeoutService.processTicket(ticket.getId(), now));
                if (result != null && result != ProcessResult.NO_ACTION) {
                    processed++;
                }
            } catch (RuntimeException ex) {
                log.error("Không xử lý được thời hạn giao của phiếu bảo trì {}", ticket.getId(), ex);
            }
        }
        if (processed > 0) {
            log.info("Đã xử lý thời hạn giao việc cho {} phiếu bảo trì", processed);
        }
        return processed;
    }
}
