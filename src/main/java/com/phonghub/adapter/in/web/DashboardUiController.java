package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.in.StatisticsUseCase.Granularity;
import com.phonghub.application.port.in.StatisticsUseCase.PeriodStat;
import com.phonghub.application.port.in.StatisticsUseCase.StatsQuery;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardUiController {

    private final PropertyUseCase propertyUseCase;
    private final RoomUseCase roomUseCase;
    private final ContractUseCase contractUseCase;
    private final MaintenanceUseCase maintenanceUseCase;
    private final CurrentUserPort currentUserPort;
    private final InvoiceUseCase invoiceUseCase;
    private final StatisticsUseCase statisticsUseCase;

    public DashboardUiController(
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        ContractUseCase contractUseCase,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort,
        InvoiceUseCase invoiceUseCase,
        StatisticsUseCase statisticsUseCase
    ) {
        this.invoiceUseCase = invoiceUseCase;
        this.statisticsUseCase = statisticsUseCase;
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.contractUseCase = contractUseCase;
        this.maintenanceUseCase = maintenanceUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(
        @RequestParam(name = "statsPropertyId", required = false) String statsPropertyId,
        @RequestParam(name = "statsFrom", required = false) String statsFrom,
        @RequestParam(name = "statsTo", required = false) String statsTo,
        Model model
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (currentUser.role() == UserRole.OWNER) {
            addOperationsAttributes(model, statsPropertyId, statsFrom, statsTo);
        } else if (currentUser.role() == UserRole.ADMIN) {
            addAdminAttributes(model);
        }
        List<Property> properties = propertyUseCase.listAccessibleProperties();

        List<Room> allRooms = new ArrayList<>();
        List<Contract> allContracts = new ArrayList<>();
        List<MaintenanceTicket> allTickets = new ArrayList<>();

        if (currentUser.role() == UserRole.TENANT) {
            contractUseCase.getActiveContractForTenant(currentUser.id()).ifPresent(allContracts::add);
            for (Property p : properties) {
                allRooms.addAll(roomUseCase.listRoomsForProperty(p.id()));
                allTickets.addAll(maintenanceUseCase.listTicketsForProperty(p.id()));
            }
            // listTicketsForProperty trả cả phiếu của phòng khác trong cùng nhà trọ; người thuê chỉ được thấy phiếu phòng mình.
            Set<UUID> ownRoomIds = allRooms.stream().map(Room::getId).collect(Collectors.toSet());
            allTickets.removeIf(t -> !ownRoomIds.contains(t.getRoomId()));
            addTenantPaymentAttributes(model, allContracts, allRooms, properties);
        } else if (currentUser.role() == UserRole.TECHNICIAN) {
            allTickets = maintenanceUseCase.listAssignedTicketsForTechnician(currentUser.id());
            for (Property p : properties) {
                allRooms.addAll(roomUseCase.listRoomsForProperty(p.id()));
            }
        } else {
            for (Property p : properties) {
                allRooms.addAll(roomUseCase.listRoomsForProperty(p.id()));
                allContracts.addAll(contractUseCase.listContractsForProperty(p.id()));
                allTickets.addAll(maintenanceUseCase.listTicketsForProperty(p.id()));
            }
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("properties", properties);
        model.addAttribute("propertiesCount", properties.size());
        model.addAttribute("roomsCount", allRooms.size());
        java.util.Map<java.util.UUID, String> roomNumbers = new java.util.HashMap<>();
        allRooms.forEach(room -> roomNumbers.put(room.getId(), room.getRoomNumber()));
        model.addAttribute("roomNumbers", roomNumbers);
        model.addAttribute("activeContractsCount", allContracts.stream().filter(Contract::isActive).count());
        model.addAttribute("openTicketsCount", allTickets.stream().filter(t -> !t.isResolved()).count());
        // Phiếu chưa hoàn thành lên trước, mới nhất trước; chờ thanh toán hiển thị cùng trạng thái
        model.addAttribute("recentTickets", allTickets.stream()
            .sorted(java.util.Comparator
                .comparing((MaintenanceTicket t) -> MaintenanceListFilter.isDone(t))
                .thenComparing(MaintenanceTicket::getCreatedAt, java.util.Comparator.reverseOrder()))
            .limit(5)
            .toList());
        model.addAttribute("ticketsNotDoneCount", allTickets.stream().filter(t -> !MaintenanceListFilter.isDone(t)).count());
        model.addAttribute("ticketsAwaitingPaymentCount", allTickets.stream()
            .filter(t -> t.getStatus() == com.phonghub.domain.model.MaintenanceStatus.AWAITING_PAYMENT).count());
        model.addAttribute("ticketsDoneCount", allTickets.stream().filter(MaintenanceListFilter::isDone).count());

        return "dashboard";
    }

    /**
     * Dashboard vận hành (#75) cho owner: số liệu lấy từ {@link StatisticsUseCase}, nơi đã kiểm tra phạm vi nhà trọ.
     * Bộ lọc sai hoặc nhà trọ ngoài phạm vi thì báo lỗi và quay về số liệu mặc định thay vì làm hỏng trang.
     */
    private void addOperationsAttributes(Model model, String propertyParam, String fromParam, String toParam) {
        YearMonth current = YearMonth.now(StatisticsQueryPort.ZONE);
        StatsQuery query;
        try {
            query = StatsQuery.of(parseUuid(propertyParam), parseMonth(fromParam), parseMonth(toParam),
                Granularity.MONTH, current);
            model.addAttribute("opsSummary", statisticsUseCase.ownerSummary(query));
        } catch (IllegalArgumentException | DomainException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            query = StatsQuery.of(null, null, null, Granularity.MONTH, current);
            model.addAttribute("opsSummary", statisticsUseCase.ownerSummary(query));
        }
        List<PeriodStat> revenue = statisticsUseCase.ownerRevenue(query);
        BigDecimal maxBilled = revenue.stream().map(PeriodStat::billed).max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        // Độ dài thanh doanh thu (% so với tháng phát sinh cao nhất) cho biểu đồ thanh ngang.
        Map<String, Integer> bars = new LinkedHashMap<>();
        for (PeriodStat r : revenue) {
            bars.put(r.period(), maxBilled.signum() == 0 ? 0
                : r.billed().multiply(BigDecimal.valueOf(100)).divide(maxBilled, 0, RoundingMode.HALF_UP).intValue());
        }

        model.addAttribute("opsQuery", query);
        model.addAttribute("opsRevenue", revenue);
        model.addAttribute("opsRevenueBars", bars);
        model.addAttribute("opsProperties", statisticsUseCase.ownerProperties(
            new StatsQuery(null, query.from(), query.to(), Granularity.MONTH)));
    }

    /** Admin vận hành nền tảng: nhà trọ, tài khoản, lấp đầy và nhà chờ duyệt; không có số liệu tiền (#163). */
    private void addAdminAttributes(Model model) {
        StatsQuery query = StatsQuery.of(null, null, null, Granularity.MONTH, YearMonth.now(StatisticsQueryPort.ZONE));
        model.addAttribute("adminOverview", statisticsUseCase.adminOverview(query));
        model.addAttribute("pendingProperties", statisticsUseCase.pendingProperties());
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Nhà trọ được chọn không hợp lệ.");
        }
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Tháng thống kê không hợp lệ (định dạng yyyy-MM).");
        }
    }

    private void addTenantPaymentAttributes(
        Model model, List<Contract> contracts, List<Room> rooms, List<Property> properties
    ) {
        LocalDate today = LocalDate.now();
        Contract contract = contracts.stream().filter(Contract::isActive).findFirst().orElse(null);
        List<Invoice> payable = contract == null
            ? List.of()
            : invoiceUseCase.listInvoicesForContract(contract.getId()).stream()
                .filter(Invoice::isPayable)
                .sorted(Comparator.comparing(Invoice::getYear).thenComparing(Invoice::getMonth))
                .toList();

        model.addAttribute("tenantContract", contract);
        model.addAttribute("tenantRoom", rooms.isEmpty() ? null : rooms.get(0));
        model.addAttribute("tenantProperty", properties.isEmpty() ? null : properties.get(0));
        model.addAttribute("payableInvoices", payable);
        model.addAttribute("nextInvoice", payable.isEmpty() ? null : payable.get(0));
        model.addAttribute("overdueInvoiceCount", payable.stream().filter(i -> i.isOverdue(today)).count());
        model.addAttribute("today", today);
    }
}
