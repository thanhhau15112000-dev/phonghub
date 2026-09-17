package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import java.math.BigDecimal;
import java.util.List;
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
@RequestMapping("/properties")
public class PropertyUiController {

    private final PropertyUseCase propertyUseCase;
    private final RoomUseCase roomUseCase;
    private final CurrentUserPort currentUserPort;

    public PropertyUiController(
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String listProperties(Model model) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        List<Property> properties = propertyUseCase.listAccessibleProperties();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("properties", properties);
        return "properties/list";
    }

    @PostMapping
    public String createProperty(
        @RequestParam String name,
        @RequestParam String address,
        @RequestParam(required = false) String description,
        @RequestParam(defaultValue = "0") int totalRooms,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Property property = propertyUseCase.createProperty(new PropertyUseCase.CreatePropertyCommand(
                name, address, description, totalRooms
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Property '" + property.name() + "' created successfully");
            return "redirect:/properties/" + property.id();
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties";
        }
    }

    @GetMapping("/{id}")
    public String viewProperty(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        try {
            Property property = propertyUseCase.getProperty(id);
            List<Room> rooms = roomUseCase.listRoomsForProperty(id);

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("property", property);
            model.addAttribute("rooms", rooms);
            return "properties/detail";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties";
        }
    }

    @PostMapping("/{id}/rooms")
    public String createRoom(
        @PathVariable UUID id,
        @RequestParam String roomNumber,
        @RequestParam(defaultValue = "1") int floor,
        @RequestParam(required = false) BigDecimal areaSqm,
        @RequestParam BigDecimal basePrice,
        @RequestParam(defaultValue = "2") int maxOccupants,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Room room = roomUseCase.createRoom(new RoomUseCase.CreateRoomCommand(
                id, roomNumber, floor, areaSqm, basePrice, maxOccupants
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Room '" + room.getRoomNumber() + "' created successfully");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + id;
    }
}
