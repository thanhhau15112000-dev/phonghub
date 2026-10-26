package com.phonghub.adapter.in.scheduling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.service.MaintenanceAssignmentTimeoutService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class MaintenanceAssignmentTimeoutSchedulerTest {

    @Test
    void startupSweepSkipsWhenTicketListCannotBeLoaded() {
        MaintenanceTicketRepositoryPort tickets = mock(MaintenanceTicketRepositoryPort.class);
        MaintenanceAssignmentTimeoutService service = mock(MaintenanceAssignmentTimeoutService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(tickets.findAll()).thenThrow(new IllegalStateException("database unavailable"));
        MaintenanceAssignmentTimeoutScheduler scheduler =
            new MaintenanceAssignmentTimeoutScheduler(tickets, service, transactions);

        assertDoesNotThrow(scheduler::onStartup);
        assertEquals(0, scheduler.run());
        verifyNoInteractions(service, transactions);
    }
}
