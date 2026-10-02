package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/rooms")
public class RoomUiController {

    private final RoomUseCase roomUseCase;
    private final PropertyUseCase propertyUseCase;
    private final ContractRepositoryPort contractRepository;
    private final MaintenanceUseCase maintenanceUseCase;
    private final CurrentUserPort currentUserPort;

    public RoomUiController(
        RoomUseCase roomUseCase,
        PropertyUseCase propertyUseCase,
        ContractRepositoryPort contractRepository,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.roomUseCase = roomUseCase;
        this.propertyUseCase = propertyUseCase;
        this.contractRepository = contractRepository;
        this.maintenanceUseCase = maintenanceUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping("/{id}")
    public String viewRoom(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        try {
            Room room = roomUseCase.getRoom(id);
            Property property = propertyUseCase.getProperty(room.getPropertyId());
            Optional<Contract> activeContract = contractRepository.findActiveByRoomId(id);
            List<MaintenanceTicket> tickets = maintenanceUseCase.listTicketsForRoom(id);

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("room", room);
            model.addAttribute("property", property);
            model.addAttribute("activeContract", activeContract.orElse(null));
            model.addAttribute("tickets", tickets);
            model.addAttribute("allStatuses", RoomStatus.values());

            return "rooms/detail";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties";
        }
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
        @PathVariable UUID id,
        @RequestParam RoomStatus status,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Room room = roomUseCase.changeRoomStatus(id, status);
            redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển trạng thái phòng sang " + UiText.INSTANCE.roomStatus(room.getStatus()) + ".");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + id;
    }

    @PostMapping("/{id}/edit")
    public String updateRoom(
        @PathVariable UUID id,
        @RequestParam String roomNumber,
        @RequestParam(defaultValue = "0") int floor,
        @RequestParam(required = false) BigDecimal areaSqm,
        @RequestParam BigDecimal basePrice,
        @RequestParam int maxOccupants,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Room room = roomUseCase.updateRoom(new RoomUseCase.UpdateRoomCommand(
                id,
                roomNumber,
                floor,
                areaSqm,
                basePrice,
                maxOccupants
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin phòng " + room.getRoomNumber() + " thành công.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + id;
    }
}
