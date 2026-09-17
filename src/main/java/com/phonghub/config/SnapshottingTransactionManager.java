package com.phonghub.config;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.Tenant;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

public class SnapshottingTransactionManager extends AbstractPlatformTransactionManager {

    private final InMemoryRoomRepository roomRepo;
    private final InMemoryMaintenanceTicketRepository ticketRepo;
    private final InMemoryContractRepository contractRepo;
    private final InMemoryTenantRepository tenantRepo;

    public static class TransactionSnapshot {
        final Map<UUID, Room> rooms;
        final Map<UUID, MaintenanceTicket> tickets;
        final Map<UUID, Contract> contracts;
        final Map<UUID, Tenant> tenants;

        public TransactionSnapshot(
            Map<UUID, Room> rooms,
            Map<UUID, MaintenanceTicket> tickets,
            Map<UUID, Contract> contracts,
            Map<UUID, Tenant> tenants
        ) {
            this.rooms = rooms;
            this.tickets = tickets;
            this.contracts = contracts;
            this.tenants = tenants;
        }
    }

    public SnapshottingTransactionManager(
        InMemoryRoomRepository roomRepo,
        InMemoryMaintenanceTicketRepository ticketRepo,
        InMemoryContractRepository contractRepo,
        InMemoryTenantRepository tenantRepo
    ) {
        this.roomRepo = roomRepo;
        this.ticketRepo = ticketRepo;
        this.contractRepo = contractRepo;
        this.tenantRepo = tenantRepo;
    }

    @Override
    protected Object doGetTransaction() {
        return new TransactionSnapshot(
            roomRepo != null ? roomRepo.snapshot() : Map.of(),
            ticketRepo != null ? ticketRepo.snapshot() : Map.of(),
            contractRepo != null ? contractRepo.snapshot() : Map.of(),
            tenantRepo != null ? tenantRepo.snapshot() : Map.of()
        );
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        if (status.getTransaction() instanceof TransactionSnapshot snapshot) {
            if (roomRepo != null) roomRepo.restore(snapshot.rooms);
            if (ticketRepo != null) ticketRepo.restore(snapshot.tickets);
            if (contractRepo != null) contractRepo.restore(snapshot.contracts);
            if (tenantRepo != null) tenantRepo.restore(snapshot.tenants);
        }
    }
}
