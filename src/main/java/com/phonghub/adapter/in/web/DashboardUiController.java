package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardUiController {

    private final PropertyUseCase propertyUseCase;
    private final RoomUseCase roomUseCase;
    private final ContractUseCase contractUseCase;
    private final MaintenanceUseCase maintenanceUseCase;
    private final CurrentUserPort currentUserPort;
    private final InvoiceUseCase invoiceUseCase;

    public DashboardUiController(
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        ContractUseCase contractUseCase,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort,
        InvoiceUseCase invoiceUseCase
    ) {
        this.invoiceUseCase = invoiceUseCase;
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.contractUseCase = contractUseCase;
        this.maintenanceUseCase = maintenanceUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
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
        model.addAttribute("activeContractsCount", allContracts.stream().filter(Contract::isActive).count());
        model.addAttribute("openTicketsCount", allTickets.stream().filter(t -> !t.isResolved()).count());
        model.addAttribute("recentTickets", allTickets.stream().limit(5).toList());

        return "dashboard";
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
