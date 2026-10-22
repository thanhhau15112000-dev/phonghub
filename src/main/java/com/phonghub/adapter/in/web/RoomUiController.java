package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.FileStorageUseCase;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
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
    private final FileStorageUseCase fileStorageUseCase;

    public RoomUiController(
        RoomUseCase roomUseCase,
        PropertyUseCase propertyUseCase,
        ContractRepositoryPort contractRepository,
        MaintenanceUseCase maintenanceUseCase,
        CurrentUserPort currentUserPort,
        RoomAssetUseCase roomAssetUseCase,
        FileStorageUseCase fileStorageUseCase
    ) {
        this.roomUseCase = roomUseCase;
        this.propertyUseCase = propertyUseCase;
        this.contractRepository = contractRepository;
        this.maintenanceUseCase = maintenanceUseCase;
        this.currentUserPort = currentUserPort;
        this.roomAssetUseCase = roomAssetUseCase;
        this.fileStorageUseCase = fileStorageUseCase;
    }

    @GetMapping("/{id}")
    public String viewRoom(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        try {
            Room room = roomUseCase.getRoom(id);
            Property property = propertyUseCase.getProperty(room.getPropertyId());
            // Hợp đồng người thuê còn ở: đang hiệu lực, hoặc đã hết hạn nhưng chưa trả phòng (#87)
            Optional<Contract> activeContract = contractRepository.findByRoomId(id).stream()
                .filter(Contract::isResiding).findFirst();
            List<MaintenanceTicket> tickets = maintenanceUseCase.listTicketsForRoom(id);

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("room", room);
            model.addAttribute("property", property);
            model.addAttribute("activeContract", activeContract.orElse(null));
            model.addAttribute("tickets", tickets);
            model.addAttribute("manualStatuses", room.manualTargets(activeContract.isPresent()));
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

    @PostMapping("/{id}/archive")
    public String archiveRoom(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Room room = roomUseCase.archiveRoom(id);
            redirectAttributes.addFlashAttribute("successMessage",
                "Đã ngừng sử dụng phòng '" + room.getRoomNumber() + "'. Có thể dùng lại ở trang nhà trọ.");
            return "redirect:/properties/" + room.getPropertyId();
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/rooms/" + id;
        }
    }

    @PostMapping("/{id}/restore")
    public String restoreRoom(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Room room = roomUseCase.restoreRoom(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã dùng lại phòng '" + room.getRoomNumber() + "'.");
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
        @RequestParam(required = false) Long version,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Room room = roomUseCase.updateRoom(new RoomUseCase.UpdateRoomCommand(
                id,
                roomNumber,
                floor,
                areaSqm,
                basePrice,
                maxOccupants,
                version
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
        // Các phòng khác cùng nhà trọ để áp dụng thêm khi thêm nhiều tài sản một lần.
        model.addAttribute("batchOtherRooms", canManage
            ? roomUseCase.listRoomsForProperty(property.id()).stream().filter(r -> !r.getId().equals(roomId)).toList()
            : List.of());
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
        @RequestParam(required = false) MultipartFile imageFile,
        @RequestParam(required = false) BigDecimal estimatedValue,
        RedirectAttributes redirectAttributes
    ) {
        return handle(id, redirectAttributes, "Đã thêm tài sản " + name.trim() + ".", () -> {
            String image = resolveImage(id, imageFile, imageUrl);
            try {
                roomAssetUseCase.create(new RoomAssetUseCase.CreateAssetCommand(
                    id, null, name, blankToNull(serialNumber), quantity, blankToNull(note), image, estimatedValue));
            } catch (RuntimeException ex) {
                discardUploaded(id, imageFile, image);
                throw ex;
            }
        });
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

    /**
     * Thêm nhiều tài sản một lần (#60, Jira "Add Facilities to Room in Batch"). Các dòng bỏ trống tên bị bỏ qua; số
     * lượng trống tính là 1.
     */
    @PostMapping("/{id}/assets/batch")
    public String createAssetBatch(
        @PathVariable UUID id,
        @RequestParam(name = "itemName", required = false) List<String> names,
        @RequestParam(name = "itemQuantity", required = false) List<String> quantities,
        @RequestParam(name = "itemValue", required = false) List<String> values,
        @RequestParam(name = "alsoRoomIds", required = false) List<UUID> alsoRoomIds,
        RedirectAttributes redirectAttributes
    ) {
        try {
            List<RoomAssetUseCase.BatchItem> items = new java.util.ArrayList<>();
            int rows = names == null ? 0 : names.size();
            for (int i = 0; i < rows; i++) {
                String name = names.get(i);
                if (name == null || name.isBlank()) {
                    continue;
                }
                int quantity = parseQuantity(valueAt(quantities, i), i + 1);
                BigDecimal value = parseValue(valueAt(values, i), i + 1);
                items.add(new RoomAssetUseCase.BatchItem(name.trim(), quantity, value));
            }
            List<RoomAsset> saved = roomAssetUseCase.createBatch(
                new RoomAssetUseCase.CreateBatchCommand(id, alsoRoomIds, items));
            long rooms = saved.stream().map(RoomAsset::roomId).distinct().count();
            redirectAttributes.addFlashAttribute("successMessage", rooms > 1
                ? String.format("Đã thêm %d tài sản cho mỗi phòng (%d phòng).", items.size(), rooms)
                : String.format("Đã thêm %d tài sản.", items.size()));
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + id + "#room-assets";
    }

    private static String valueAt(List<String> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }

    private static int parseQuantity(String raw, int row) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Số lượng ở dòng " + row + " không hợp lệ.");
        }
    }

    private static BigDecimal parseValue(String raw, int row) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Giá trị ước tính ở dòng " + row + " không hợp lệ.");
        }
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
        @RequestParam(required = false) MultipartFile imageFile,
        @RequestParam(required = false) BigDecimal estimatedValue,
        RedirectAttributes redirectAttributes
    ) {
        return handle(id, redirectAttributes, "Đã cập nhật tài sản " + name.trim() + ".", () -> {
            String image = resolveImage(id, imageFile, imageUrl);
            try {
                roomAssetUseCase.update(new RoomAssetUseCase.UpdateAssetCommand(
                    assetId, name, blankToNull(serialNumber), quantity, blankToNull(note), image, estimatedValue));
            } catch (RuntimeException ex) {
                discardUploaded(id, imageFile, image);
                throw ex;
            }
        });
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

    @GetMapping("/{id}/assets/{assetId}/image")
    public ResponseEntity<Void> assetImage(@PathVariable UUID id, @PathVariable UUID assetId) {
        RoomAsset asset = roomAssetUseCase.get(assetId);
        if (!asset.roomId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        java.net.URI target = roomAssetUseCase.imageSignedUrl(assetId);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private String resolveImage(UUID roomId, MultipartFile imageFile, String imageUrl) {
        if (imageFile == null || imageFile.isEmpty()) {
            return blankToNull(imageUrl);
        }
        Room room = roomUseCase.getRoom(roomId);
        try {
            return fileStorageUseCase.upload(new FileStorageUseCase.UploadFileCommand(
                room.getPropertyId(), "assets", imageFile.getOriginalFilename(), imageFile.getContentType(),
                imageFile.getBytes())).objectKey();
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Không đọc được tệp ảnh", ex);
        }
    }

    private void discardUploaded(UUID roomId, MultipartFile imageFile, String objectKey) {
        if (imageFile == null || imageFile.isEmpty() || objectKey == null) {
            return;
        }
        try {
            fileStorageUseCase.delete(roomUseCase.getRoom(roomId).getPropertyId(), objectKey);
        } catch (RuntimeException ignored) {
            // best effort: the original failure is reported to the user
        }
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
