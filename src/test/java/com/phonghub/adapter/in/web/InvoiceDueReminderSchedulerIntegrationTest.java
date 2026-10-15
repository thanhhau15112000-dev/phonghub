package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.in.scheduling.InvoiceDueReminderScheduler;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceReminderRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** #86: khi bật job, scheduler nhắc hóa đơn được tạo và chạy trong transaction của ứng dụng (profile test tắt mặc định). */
@TestPropertySource(properties = "phonghub.invoicing.due-reminder-enabled=true")
class InvoiceDueReminderSchedulerIntegrationTest extends SeededWebTest {

    @Autowired private InvoiceDueReminderScheduler scheduler;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryInvoiceReminderRepository reminders;
    @Autowired private InMemoryNotificationRepository notifications;

    @BeforeEach
    void reset() {
        invoices.clear();
        reminders.clear();
        notifications.clear();
    }

    @Test
    @DisplayName("Scheduler reminds the tenant about an invoice due tomorrow exactly once")
    void schedulerRemindsOnce() {
        Contract contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        Invoice invoice = invoices.save(Invoice.issueMonthlyRent(contract, YearMonth.now(), "PHDUE01", List.of()));

        assertEquals(1, scheduler.run(invoice.getDueDate().minusDays(1)));
        assertEquals(0, scheduler.run(invoice.getDueDate().minusDays(1)));
        assertTrue(notifications.findAll().stream().anyMatch(n -> n.title().equals("Hóa đơn sắp đến hạn")
            && LocalDemoAuthenticationAdapter.TENANT_1_ID.equals(n.targetUserId())));
    }
}
