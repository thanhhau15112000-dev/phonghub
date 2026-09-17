package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.util.ArrayList;
import java.util.List;
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

    public DashboardUiController(
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        ContractUseCase contractUseCase,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort
    ) {
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
}
