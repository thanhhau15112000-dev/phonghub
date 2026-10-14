package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Bản in-memory của truy vấn thống kê cho profile test; cùng quy ước với bản PostgreSQL. */
public class InMemoryStatisticsQueryAdapter implements StatisticsQueryPort {

    private static final Set<InvoiceStatus> UNPAID = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);
    private static final Set<MaintenanceStatus> OPEN = EnumSet.of(
        MaintenanceStatus.REPORTED, MaintenanceStatus.ASSIGNED, MaintenanceStatus.IN_PROGRESS);
    private static final Set<MaintenanceStatus> WORK_DONE = EnumSet.of(
        MaintenanceStatus.RESOLVED, MaintenanceStatus.AWAITING_PAYMENT);

    private final InMemoryInvoiceRepository invoices;
    private final ContractRepositoryPort contracts;
    private final RoomRepositoryPort rooms;
    private final PropertyRepositoryPort properties;
    private final MaintenanceTicketRepositoryPort tickets;
    private final UserRepositoryPort users;
    private final AuditPort audit;

    public InMemoryStatisticsQueryAdapter(
        InMemoryInvoiceRepository invoices,
        ContractRepositoryPort contracts,
        RoomRepositoryPort rooms,
        PropertyRepositoryPort properties,
        MaintenanceTicketRepositoryPort tickets,
        UserRepositoryPort users,
        AuditPort audit
    ) {
        this.invoices = invoices;
        this.contracts = contracts;
        this.rooms = rooms;
        this.properties = properties;
        this.tickets = tickets;
        this.users = users;
        this.audit = audit;
    }

    @Override
    public List<InvoiceAggregate> invoiceAggregates(
        Collection<UUID> propertyIds, YearMonth from, YearMonth to, LocalDate today
    ) {
        Predicate<UUID> inScope = scope(propertyIds);
        record Key(UUID propertyId, YearMonth period) {}
        Map<Key, List<Invoice>> grouped = invoices.findAll().stream()
            .filter(i -> i.getStatus() != InvoiceStatus.VOIDED && !i.isConsolidated())
            .filter(i -> {
                YearMonth period = YearMonth.of(i.getYear(), i.getMonth());
                return !period.isBefore(from) && !period.isAfter(to);
            })
            .map(i -> Map.entry(propertyOf(i), i))
            .filter(e -> e.getKey().isPresent() && inScope.test(e.getKey().get()))
            .collect(Collectors.groupingBy(
                e -> new Key(e.getKey().get(), YearMonth.of(e.getValue().getYear(), e.getValue().getMonth())),
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        List<InvoiceAggregate> result = new ArrayList<>();
        grouped.forEach((key, list) -> {
            List<Invoice> unpaid = list.stream().filter(i -> UNPAID.contains(i.getStatus())).toList();
            List<Invoice> overdue = unpaid.stream().filter(i -> i.getDueDate().isBefore(today)).toList();
            result.add(new InvoiceAggregate(key.propertyId(), key.period(), list.size(),
                sum(list.stream().map(Invoice::getTotalAmount).toList()),
                sum(list.stream().map(Invoice::getPaidAmount).toList()),
                sum(unpaid.stream().map(Invoice::remainingAmount).toList()),
                overdue.size(),
                sum(overdue.stream().map(Invoice::remainingAmount).toList())));
        });
        return result;
    }

    @Override
    public List<RoomStatusCount> roomStatusCounts(Collection<UUID> propertyIds) {
        Collection<UUID> ids = propertyIds != null ? propertyIds : properties.findAll().stream().map(Property::id).toList();
        List<RoomStatusCount> result = new ArrayList<>();
        for (UUID propertyId : ids) {
            rooms.findByPropertyId(propertyId).stream()
                .collect(Collectors.groupingBy(Room::getStatus, Collectors.counting()))
                .forEach((status, count) -> result.add(new RoomStatusCount(propertyId, status, count)));
        }
        return result;
    }

    @Override
    public List<MaintenanceAggregate> completedMaintenance(Collection<UUID> propertyIds, Instant from, Instant to) {
        Predicate<UUID> inScope = scope(propertyIds);
        record Key(UUID propertyId, YearMonth period) {}
        record Done(MaintenanceTicket ticket, Instant at) {}
        Map<Key, List<Done>> grouped = new java.util.HashMap<>();
        for (MaintenanceTicket ticket : tickets.findAll()) {
            if (ticket.getPropertyId() == null || !inScope.test(ticket.getPropertyId())) {
                continue;
            }
            Optional<Instant> doneAt = tickets.findEvents(ticket.getId()).stream()
                .filter(e -> WORK_DONE.contains(e.toStatus()))
                .map(MaintenanceTicketEvent::at)
                .min(Comparator.naturalOrder());
            if (doneAt.isEmpty() || doneAt.get().isBefore(from) || !doneAt.get().isBefore(to)) {
                continue;
            }
            YearMonth period = YearMonth.from(doneAt.get().atZone(ZONE));
            grouped.computeIfAbsent(new Key(ticket.getPropertyId(), period), k -> new ArrayList<>())
                .add(new Done(ticket, doneAt.get()));
        }

        List<MaintenanceAggregate> result = new ArrayList<>();
        grouped.forEach((key, list) -> result.add(new MaintenanceAggregate(key.propertyId(), key.period(), list.size(),
            sum(list.stream()
                .filter(d -> d.ticket().getLiableParty() != LiableParty.TENANT)
                .map(d -> d.ticket().getRepairCost())
                .toList()),
            list.stream()
                .mapToDouble(d -> Duration.between(d.ticket().getCreatedAt(), d.at()).toSeconds() / 3600.0)
                .sum())));
        return result;
    }

    @Override
    public List<OpenMaintenanceCount> openMaintenance(Collection<UUID> propertyIds, Instant now) {
        Predicate<UUID> inScope = scope(propertyIds);
        return tickets.findAll().stream()
            .filter(t -> t.getPropertyId() != null && inScope.test(t.getPropertyId()))
            .filter(t -> OPEN.contains(t.getStatus()))
            .collect(Collectors.groupingBy(MaintenanceTicket::getPropertyId))
            .entrySet().stream()
            .map(e -> new OpenMaintenanceCount(e.getKey(), e.getValue().size(),
                e.getValue().stream().filter(t -> t.getDueAt() != null && t.getDueAt().isBefore(now)).count()))
            .toList();
    }

    @Override
    public List<ApprovalCount> propertyApprovalCounts() {
        return properties.findAll().stream()
            .collect(Collectors.groupingBy(
                p -> p.approvalStatus() != null ? p.approvalStatus() : PropertyApprovalStatus.VERIFIED,
                Collectors.counting()))
            .entrySet().stream()
            .map(e -> new ApprovalCount(e.getKey(), e.getValue()))
            .toList();
    }

    @Override
    public List<PendingPropertyRow> pendingProperties() {
        return properties.findAll().stream()
            .filter(p -> p.approvalStatus() == PropertyApprovalStatus.PENDING)
            .sorted(Comparator.comparing(Property::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(p -> {
                Optional<User> owner = p.ownerId() == null ? Optional.empty() : users.findById(p.ownerId());
                return new PendingPropertyRow(p.id(), p.name(), p.address(), p.ownerId(),
                    owner.map(User::fullName).orElse(null), owner.map(User::email).orElse(null), p.createdAt());
            })
            .toList();
    }

    @Override
    public List<UserCount> userCounts() {
        record Key(com.phonghub.domain.model.UserRole role, User.UserStatus status) {}
        return users.findAll().stream()
            .collect(Collectors.groupingBy(u -> new Key(u.role(), u.status()), Collectors.counting()))
            .entrySet().stream()
            .map(e -> new UserCount(e.getKey().role(), e.getKey().status(), e.getValue()))
            .toList();
    }

    @Override
    public List<SignupCount> userSignups(Instant from, Instant to) {
        record Key(YearMonth period, com.phonghub.domain.model.UserRole role) {}
        return users.findAll().stream()
            .filter(u -> u.createdAt() != null && !u.createdAt().isBefore(from) && u.createdAt().isBefore(to))
            .collect(Collectors.groupingBy(u -> new Key(YearMonth.from(u.createdAt().atZone(ZONE)), u.role()),
                Collectors.counting()))
            .entrySet().stream()
            .map(e -> new SignupCount(e.getKey().period(), e.getKey().role(), e.getValue()))
            .toList();
    }

    @Override
    public List<ActivityCount> auditActivity(Instant from, Instant to) {
        if (!(audit instanceof InMemoryAuditAdapter inMemory)) {
            return List.of();
        }
        return inMemory.getRecordedEvents().stream()
            .filter(e -> !e.timestamp().isBefore(from) && e.timestamp().isBefore(to))
            .collect(Collectors.groupingBy(e -> YearMonth.from(e.timestamp().atZone(ZONE))))
            .entrySet().stream()
            .map(e -> {
                Set<UUID> actors = new HashSet<>();
                e.getValue().stream().map(AuditPort.AuditEvent::actorId).filter(Objects::nonNull).forEach(actors::add);
                return new ActivityCount(e.getKey(), e.getValue().size(), actors.size());
            })
            .toList();
    }

    private Optional<UUID> propertyOf(Invoice invoice) {
        return contracts.findById(invoice.getContractId()).map(Contract::getPropertyId);
    }

    private static Predicate<UUID> scope(Collection<UUID> propertyIds) {
        if (propertyIds == null) {
            return id -> true;
        }
        Set<UUID> allowed = Set.copyOf(propertyIds);
        return allowed::contains;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
