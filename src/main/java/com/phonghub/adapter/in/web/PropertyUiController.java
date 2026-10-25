package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.FileStorageUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.RoomSetupUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.PropertyApprovalStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
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
    private final RoomSetupUseCase roomSetupUseCase;
    private final FileStorageUseCase fileStorageUseCase;
    private final AuthorizationService authorizationService;

    public PropertyUiController(
        PropertyUseCase propertyUseCase,
        RoomUseCase roomUseCase,
        CurrentUserPort currentUserPort,
        RoomSetupUseCase roomSetupUseCase,
        FileStorageUseCase fileStorageUseCase,
        AuthorizationService authorizationService
    ) {
        this.propertyUseCase = propertyUseCase;
        this.roomUseCase = roomUseCase;
        this.currentUserPort = currentUserPort;
        this.roomSetupUseCase = roomSetupUseCase;
        this.fileStorageUseCase = fileStorageUseCase;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public String listProperties(@RequestParam(required = false) String status, Model model) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        List<Property> allProperties = propertyUseCase.listAccessibleProperties();

        Map<String, Long> statusCounts = allProperties.stream()
            .collect(Collectors.groupingBy(p -> p.approvalStatus().name(), Collectors.counting()));

        PropertyApprovalStatus filterStatus = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                filterStatus = PropertyApprovalStatus.fromString(status);
            } catch (Exception ignored) {
                filterStatus = null;
            }
        }

        final PropertyApprovalStatus finalFilter = filterStatus;
        List<Property> filteredProperties = finalFilter == null
            ? allProperties
            : allProperties.stream().filter(p -> p.approvalStatus() == finalFilter).toList();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("properties", filteredProperties);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("selectedStatus", finalFilter != null ? finalFilter.name() : "ALL");
        model.addAttribute("totalCount", allProperties.size());
        model.addAttribute("archivedProperties", propertyUseCase.listArchivedProperties());
        return "properties/list";
    }

    @PostMapping("/{id}/archive")
    public String archiveProperty(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Property property = propertyUseCase.archiveProperty(id);
            redirectAttributes.addFlashAttribute("successMessage",
                "Đã ẩn nhà trọ '" + property.name() + "'. Có thể khôi phục trong mục \"Nhà trọ đã ẩn\".");
            return "redirect:/properties";
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties/" + id;
        }
    }

    @PostMapping("/{id}/restore")
    public String restoreProperty(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Property property = propertyUseCase.restoreProperty(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã khôi phục nhà trọ '" + property.name() + "'.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + id;
    }

    @PostMapping("/{id}/verify")
    public String verifyProperty(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Property property = propertyUseCase.verifyProperty(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã duyệt nhà trọ '" + property.name() + "' thành công.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties";
    }

    @PostMapping("/{id}/reject")
    public String rejectProperty(
        @PathVariable UUID id,
        @RequestParam(required = false) String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Property property = propertyUseCase.rejectProperty(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối nhà trọ '" + property.name() + "'.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties";
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
            if (property.approvalStatus() == PropertyApprovalStatus.PENDING) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã gửi thông tin nhà trọ '" + property.name() + "' để chờ Quản trị viên duyệt.");
                return "redirect:/properties";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo nhà trọ '" + property.name() + "'.");
            return "redirect:/properties/" + property.id();
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties";
        }
    }

    @GetMapping("/{id}")
    public String viewProperty(
        @PathVariable UUID id,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String minPrice,
        @RequestParam(required = false) String maxPrice,
        @RequestParam(required = false) String floor,
        @RequestParam(defaultValue = "0") int page,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        try {
            Property property = propertyUseCase.getProperty(id);

            RoomPage roomPage;
            try {
                roomPage = roomUseCase.searchRooms(new RoomSearchCriteria(
                    id, parseStatus(status), parseDecimal(minPrice), parseDecimal(maxPrice), parseInteger(floor),
                    null, Math.max(0, page), ROOM_PAGE_SIZE));
            } catch (IllegalArgumentException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
                roomPage = roomUseCase.searchRooms(new RoomSearchCriteria(
                    id, null, null, null, null, null, 0, ROOM_PAGE_SIZE));
                status = minPrice = maxPrice = floor = null;
            }
            if (roomPage.items().isEmpty() && roomPage.page() > 0 && roomPage.totalElements() > 0) {
                // Trang vượt quá số trang hiện có (ví dụ link cũ sau khi xóa phòng): về trang cuối.
                roomPage = roomUseCase.searchRooms(new RoomSearchCriteria(
                    id, parseStatus(status), parseDecimal(minPrice), parseDecimal(maxPrice), parseInteger(floor),
                    null, roomPage.totalPages() - 1, ROOM_PAGE_SIZE));
            }

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("property", property);
            model.addAttribute("canManageRoomsAndAssets", authorizationService.canManageRoomsAndAssets(currentUser, id));
            model.addAttribute("rooms", roomPage.items());
            model.addAttribute("roomPage", roomPage);
            model.addAttribute("roomStatuses", RoomStatus.values());
            model.addAttribute("filterStatus", status);
            model.addAttribute("filterMinPrice", minPrice);
            model.addAttribute("filterMaxPrice", maxPrice);
            model.addAttribute("filterFloor", floor);
            model.addAttribute("roomFilterActive", isPresent(status) || isPresent(minPrice) || isPresent(maxPrice)
                || isPresent(floor));
            // Số phòng đã tạo thật (đang dùng) để so với số phòng chủ trọ khai báo lúc tạo nhà trọ
            model.addAttribute("createdRoomCount", roomUseCase.listRoomsForProperty(id).size());
            model.addAttribute("archivedRooms", roomUseCase.searchRooms(new RoomSearchCriteria(
                id, null, null, null, null, null, 0, null, true)).items());
            return "properties/detail";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/properties";
        }
    }

    private static final int ROOM_PAGE_SIZE = 10;

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static RoomStatus parseStatus(String value) {
        if (!isPresent(value)) {
            return null;
        }
        try {
            return RoomStatus.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Trạng thái phòng không hợp lệ.");
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (!isPresent(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Giá lọc không hợp lệ.");
        }
    }

    private static Integer parseInteger(String value) {
        if (!isPresent(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Tầng lọc không hợp lệ.");
        }
    }

    @PostMapping("/{id}/edit")
    public String updateProperty(
        @PathVariable UUID id,
        @RequestParam String name,
        @RequestParam String address,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Integer totalRooms,
        RedirectAttributes redirectAttributes
    ) {
        try {
            Property before = propertyUseCase.getProperty(id);
            Property saved = propertyUseCase.updateProperty(
                new PropertyUseCase.UpdatePropertyCommand(id, name, address, description, totalRooms));
            boolean resubmitted = before.approvalStatus() != saved.approvalStatus()
                && saved.approvalStatus() == com.phonghub.domain.model.PropertyApprovalStatus.PENDING;
            redirectAttributes.addFlashAttribute("successMessage", resubmitted
                ? "Đã cập nhật. Đổi địa chỉ cần được quản trị viên duyệt lại."
                : "Đã cập nhật thông tin nhà trọ.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + id;
    }

    @PostMapping("/{id}/rooms")
    public String createRoom(
        @PathVariable UUID id,
        @RequestParam String roomNumber,
        @RequestParam(defaultValue = "1") int floor,
        @RequestParam(required = false) BigDecimal areaSqm,
        @RequestParam BigDecimal basePrice,
        @RequestParam(defaultValue = "2") int maxOccupants,
        @RequestParam BigDecimal electricMeter,
        @RequestParam BigDecimal waterMeter,
        @RequestParam String conditionNote,
        @RequestParam(defaultValue = "false") boolean noAssets,
        @RequestParam(required = false) String assetTemplate,
        @RequestParam(required = false) List<String> assetName,
        @RequestParam(required = false) List<String> assetQuantity,
        @RequestParam(required = false) List<String> assetStatus,
        @RequestParam(required = false) List<String> assetNote,
        @RequestParam(required = false) List<String> assetValue,
        @RequestParam(required = false) MultipartFile electricMeterPhoto,
        @RequestParam(required = false) MultipartFile waterMeterPhoto,
        RedirectAttributes redirectAttributes
    ) {
        String electricPhotoKey = null;
        String waterPhotoKey = null;
        try {
            List<RoomSetupUseCase.AssetInput> assets = parseAssetInputs(assetName, assetQuantity, assetStatus, assetNote, assetValue);
            electricPhotoKey = uploadConditionPhoto(id, electricMeterPhoto);
            waterPhotoKey = uploadConditionPhoto(id, waterMeterPhoto);
            var created = roomSetupUseCase.createRoom(new RoomSetupUseCase.CreateRoomCommand(
                id, roomNumber, floor, areaSqm, basePrice, maxOccupants, electricMeter, waterMeter, conditionNote,
                noAssets, assetTemplate, assets, electricPhotoKey, waterPhotoKey));
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo phòng '" + created.room().getRoomNumber() + "' kèm biên bản hiện trạng.");
        } catch (DomainException | IllegalArgumentException ex) {
            discardConditionPhoto(id, electricPhotoKey);
            discardConditionPhoto(id, waterPhotoKey);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (RuntimeException ex) {
            discardConditionPhoto(id, electricPhotoKey);
            discardConditionPhoto(id, waterPhotoKey);
            throw ex;
        }
        return "redirect:/properties/" + id;
    }

    @PostMapping("/{id}/rooms/batch")
    public String createRoomsBatch(
        @PathVariable UUID id,
        @RequestParam String roomNumbers,
        @RequestParam(defaultValue = "1") int floor,
        @RequestParam(required = false) BigDecimal areaSqm,
        @RequestParam BigDecimal basePrice,
        @RequestParam(defaultValue = "2") int maxOccupants,
        @RequestParam String conditionNote,
        @RequestParam String assetTemplate,
        @RequestParam(required = false) List<String> electricMeters,
        @RequestParam(required = false) List<String> waterMeters,
        RedirectAttributes redirectAttributes
    ) {
        try {
            List<String> roomNames = expandRoomNumbers(roomNumbers);
            if (electricMeters == null || electricMeters.size() != roomNames.size()
                || waterMeters == null || waterMeters.size() != roomNames.size()) {
                throw new IllegalArgumentException("Cần nhập chỉ số điện và nước cho từng phòng trong danh sách.");
            }
            List<RoomSetupUseCase.RoomReadingInput> readings = new java.util.ArrayList<>();
            for (int i = 0; i < roomNames.size(); i++) {
                readings.add(new RoomSetupUseCase.RoomReadingInput(roomNames.get(i),
                    parseMeter(electricMeters.get(i), i + 1, "điện"), parseMeter(waterMeters.get(i), i + 1, "nước")));
            }
            var created = roomSetupUseCase.createRooms(new RoomSetupUseCase.BulkCreateCommand(
                id, floor, areaSqm, basePrice, maxOccupants, conditionNote, assetTemplate, readings));
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo " + created.size()
                + " phòng cùng biên bản hiện trạng.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + id;
    }

    private String uploadConditionPhoto(UUID propertyId, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            return fileStorageUseCase.upload(new FileStorageUseCase.UploadFileCommand(propertyId, "room-condition",
                file.getOriginalFilename(), file.getContentType(), file.getBytes())).objectKey();
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Không đọc được ảnh đồng hồ.", ex);
        }
    }

    private void discardConditionPhoto(UUID propertyId, String objectKey) {
        if (objectKey == null) return;
        try { fileStorageUseCase.delete(propertyId, objectKey); } catch (RuntimeException ignored) { }
    }

    private static List<RoomSetupUseCase.AssetInput> parseAssetInputs(List<String> names, List<String> quantities,
            List<String> statuses, List<String> notes, List<String> values) {
        List<RoomSetupUseCase.AssetInput> assets = new java.util.ArrayList<>();
        if (names == null) return assets;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) continue;
            int quantity;
            BigDecimal value = null;
            AssetStatus status;
            try {
                quantity = Integer.parseInt(valueAt(quantities, i, "1"));
                String valueText = valueAt(values, i, "");
                if (!valueText.isBlank()) value = new BigDecimal(valueText.trim());
                status = AssetStatus.valueOf(valueAt(statuses, i, "GOOD"));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Thông tin tài sản ở dòng " + (i + 1) + " không hợp lệ.");
            }
            assets.add(new RoomSetupUseCase.AssetInput(name.trim(), quantity, status,
                blankToNull(valueAt(notes, i, "")), value));
        }
        return assets;
    }

    private static String valueAt(List<String> values, int index, String fallback) {
        return values == null || index >= values.size() || values.get(index) == null ? fallback : values.get(index).trim();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static BigDecimal parseMeter(String raw, int row, String kind) {
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() < 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Chỉ số " + kind + " của phòng thứ " + row + " không hợp lệ.");
        }
    }

    private static List<String> expandRoomNumbers(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Cần nhập danh sách số phòng.");
        List<String> result = new java.util.ArrayList<>();
        for (String token : input.trim().split("[,;\\s]+")) {
            if (token.isBlank()) continue;
            java.util.regex.Matcher range = java.util.regex.Pattern.compile("^(.+?)(\\d+)[-–](?:(.+?))?(\\d+)$").matcher(token);
            if (!range.matches()) { result.add(token); continue; }
            String prefix = range.group(1);
            String endPrefix = range.group(3);
            if (endPrefix != null && !endPrefix.equalsIgnoreCase(prefix)) {
                throw new IllegalArgumentException("Khoảng số phòng phải cùng tiền tố.");
            }
            int from = Integer.parseInt(range.group(2));
            int to = Integer.parseInt(range.group(4));
            if (to < from || to - from + 1 > RoomSetupUseCase.BulkCreateCommand.MAX_ROOMS) {
                throw new IllegalArgumentException("Khoảng số phòng không hợp lệ hoặc vượt quá 50 phòng.");
            }
            int width = Math.max(range.group(2).length(), range.group(4).length());
            for (int n = from; n <= to; n++) result.add(prefix + String.format("%0" + width + "d", n));
        }
        if (result.isEmpty() || result.size() > RoomSetupUseCase.BulkCreateCommand.MAX_ROOMS) {
            throw new IllegalArgumentException("Mỗi lần tạo từ 1 đến 50 phòng.");
        }
        return List.copyOf(result);
    }
}
