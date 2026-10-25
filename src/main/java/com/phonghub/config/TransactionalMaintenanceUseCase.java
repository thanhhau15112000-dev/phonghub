package com.phonghub.config;

import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalMaintenanceUseCase implements MaintenanceUseCase {

    private final MaintenanceUseCase delegate;

    public TransactionalMaintenanceUseCase(MaintenanceUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket createTicket(CreateMaintenanceTicketCommand command) {
        return delegate.createTicket(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket acceptTicket(UUID ticketId) {
        return delegate.acceptTicket(ticketId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceTicketEvent> getTimeline(UUID ticketId) {
        return delegate.getTimeline(ticketId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket assignTechnician(UUID ticketId, UUID technicianId) {
        return assignTechnician(ticketId, technicianId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket assignTechnician(UUID ticketId, UUID technicianId, String reason) {
        return delegate.assignTechnician(ticketId, technicianId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket rejectTicket(UUID ticketId, String reason) {
        return delegate.rejectTicket(ticketId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket updateTicket(UpdateMaintenanceTicketCommand command) {
        return delegate.updateTicket(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket cancelTicket(UUID ticketId, String reason) {
        return delegate.cancelTicket(ticketId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket resolveTicket(ResolveMaintenanceTicketCommand command) {
        return delegate.resolveTicket(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket quoteTicket(UUID ticketId, java.math.BigDecimal amount, String note) {
        return delegate.quoteTicket(ticketId, amount, note);
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceTicket.Quote visibleQuote(UUID ticketId) {
        return delegate.visibleQuote(ticketId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket verifyTicket(UUID ticketId) {
        return delegate.verifyTicket(ticketId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket reopenTicket(UUID ticketId, String feedback) {
        return delegate.reopenTicket(ticketId, feedback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket waiveRepairFee(UUID ticketId, String reason) {
        return delegate.waiveRepairFee(ticketId, reason);
    }

    @Override
    public MaintenanceTicket getTicket(UUID ticketId) {
        return delegate.getTicket(ticketId);
    }

    @Override
    public List<MaintenanceTicket> listTicketsForProperty(UUID propertyId) {
        return delegate.listTicketsForProperty(propertyId);
    }

    @Override
    public List<MaintenanceTicket> listAssignedTicketsForTechnician(UUID technicianId) {
        return delegate.listAssignedTicketsForTechnician(technicianId);
    }

    @Override
    public List<MaintenanceTicket> listTicketsForRoom(UUID roomId) {
        return delegate.listTicketsForRoom(roomId);
    }
}
