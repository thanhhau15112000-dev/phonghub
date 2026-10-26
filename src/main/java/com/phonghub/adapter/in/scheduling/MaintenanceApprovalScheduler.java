package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.service.MaintenanceApprovalService;
import com.phonghub.application.service.MaintenanceApprovalService.ProcessResult;
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

/**
 * Mỗi giờ và lúc khởi động: phiếu quá hạn chủ trọ duyệt (3 ngày) tự chuyển xử lý, phiếu quá hạn người thuê phê duyệt
 * báo giá (7 ngày) tự hủy, và nhắc trước hạn. Mỗi phiếu được xử lý trong một transaction riêng (#202, #203).
 */
@Component
public class MaintenanceApprovalScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceApprovalScheduler.class);

    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final MaintenanceApprovalService approvalService;
    private final TransactionTemplate transactionTemplate;

    public MaintenanceApprovalScheduler(
        MaintenanceTicketRepositoryPort ticketRepository,
        MaintenanceApprovalService approvalService,
        TransactionTemplate transactionTemplate
    ) {
        this.ticketRepository = ticketRepository;
        this.approvalService = approvalService;
        this.transactionTemplate = transactionTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void hourly() {
        run();
    }

    public int run() {
        Instant now = Instant.now();
        int processed = 0;
        List<MaintenanceTicket> tickets;
        try {
            tickets = ticketRepository.findAll().stream().filter(MaintenanceTicket::isPendingApproval).toList();
        } catch (RuntimeException ex) {
            log.error("Không tải được danh sách phiếu bảo trì chờ duyệt", ex);
            return 0;
        }
        for (MaintenanceTicket ticket : tickets) {
            try {
                ProcessResult result = transactionTemplate.execute(
                    status -> approvalService.processTicket(ticket.getId(), now));
                if (result != null && result != ProcessResult.NO_ACTION) {
                    processed++;
                }
            } catch (RuntimeException ex) {
                log.error("Không xử lý được hạn duyệt của phiếu bảo trì {}", ticket.getId(), ex);
            }
        }
        if (processed > 0) {
            log.info("Đã xử lý hạn duyệt cho {} phiếu bảo trì", processed);
        }
        return processed;
    }
}
