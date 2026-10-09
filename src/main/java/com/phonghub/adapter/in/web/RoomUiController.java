package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomAssetEvent;
import com.phonghub.domain.model.UserRole;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
    private final RoomAssetUseCase roomAssetUseCase;

    public RoomUiController(
        RoomUseCase roomUseCase,
        PropertyUseCase propertyUseCase,
        ContractRepositoryPort contractRepository,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort,
        RoomAssetUseCase roomAssetUseCase
    ) {
        this.roomUseCase = roomUseCase;
        this.propertyUseCase = propertyUseCase;
        this.contractRepository = contractRepository;
        this.maintenanceUseCase = maintenanceUseCase;
        this.currentUserPort = currentUserPort;
        this.roomAssetUseCase = roomAssetUseCase;
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
            addAssetAttributes(model, currentUser, property, id);

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

    private void addAssetAttributes(Model model, CurrentUser user, Property property, UUID roomId) {
        boolean canManage = user != null && (user.role() == UserRole.ADMIN || user.role() == UserRole.STAFF
            || (user.role() == UserRole.OWNER && user.id().equals(property.ownerId())));
        List<RoomAsset> assets;
        try {
            assets = roomAssetUseCase.list(roomId);
        } catch (UnauthorizedPropertyAccessException ex) {
            assets = List.of();
        }
        Map<UUID, List<RoomAssetEvent>> history = new LinkedHashMap<>();
        if (canManage) {
            for (RoomAsset asset : assets) {
                history.put(asset.id(), roomAssetUseCase.history(asset.id()));
            }
        }
        model.addAttribute("assets", assets);
        model.addAttribute("assetHistory", history);
        model.addAttribute("canManageAssets", canManage);
        model.addAttribute("assetEventTypes", AssetEventType.values());
    }

    @PostMapping("/{id}/assets")
    public String createAsset(
        @PathVariable UUID id,
        @RequestParam String name,
        @RequestParam(required = false) String serialNumber,
        @RequestParam(defaultValue = "1") int quantity,
        @RequestParam(required = false) String note,
        @RequestParam(required = false) String imageUrl,
        @RequestParam(required = false) BigDecimal estimatedValue,
        RedirectAttributes redirectAttributes
    ) {
        return handle(id, redirectAttributes, "Đã thêm tài sản " + name.trim() + ".", () ->
            roomAssetUseCase.create(new RoomAssetUseCase.CreateAssetCommand(
                id, null, name, blankToNull(serialNumber), quantity, blankToNull(note), blankToNull(imageUrl),
                estimatedValue)));
    }

    @PostMapping("/{id}/assets/template")
    public String applyTemplate(
        @PathVariable UUID id,
        @RequestParam String template,
        RedirectAttributes redirectAttributes
    ) {
        return handle(id, redirectAttributes, "Đã thêm tài sản từ mẫu " + template + ".", () ->
            roomAssetUseCase.createFromTemplate(id, template));
    }

    @PostMapping("/{id}/assets/{assetId}/edit")
    public String editAsset(
        @PathVariable UUID id,
        @PathVariable UUID assetId,
        @RequestParam String name,
        @RequestParam(required = false) String serialNumber,
        @RequestParam(defaultValue = "1") int quantity,
        @RequestParam(required = false) String note,
        @RequestParam(required = false) String imageUrl,
        @RequestParam(required = false) BigDecimal estimatedValue,
        RedirectAttributes redirectAttributes
    ) {
        return handle(id, redirectAttributes, "Đã cập nhật tài sản " + name.trim() + ".", () ->
            roomAssetUseCase.update(new RoomAssetUseCase.UpdateAssetCommand(
                assetId, name, blankToNull(serialNumber), quantity, blankToNull(note), blankToNull(imageUrl),
                estimatedValue)));
    }

    @PostMapping("/{id}/assets/{assetId}/delete")
    public String deleteAsset(
        @PathVariable UUID id,
        @PathVariable UUID assetId,
        RedirectAttributes redirectAttributes
    ) {
        return handle(id, redirectAttributes, "Đã xóa tài sản.", () -> roomAssetUseCase.delete(assetId));
    }

    @PostMapping("/{id}/assets/{assetId}/events")
    public String recordAssetEvent(
        @PathVariable UUID id,
        @PathVariable UUID assetId,
        @RequestParam AssetEventType type,
        @RequestParam String description,
        @RequestParam(required = false) BigDecimal cost,
        RedirectAttributes redirectAttributes
    ) {
        return handle(id, redirectAttributes, "Đã ghi nhận sự kiện tài sản.", () ->
            roomAssetUseCase.recordEvent(new RoomAssetUseCase.RecordEventCommand(
                assetId, type, description, cost, null)));
    }

    @GetMapping(value = "/{id}/assets/{assetId}/qr", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> assetQr(
        @PathVariable UUID id,
        @PathVariable UUID assetId,
        HttpServletRequest request
    ) {
        RoomAsset asset = roomAssetUseCase.get(assetId);
        if (!asset.roomId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        return AssetQr.response(asset, request);
    }

    private String handle(UUID roomId, RedirectAttributes redirectAttributes, String successMessage, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + roomId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
