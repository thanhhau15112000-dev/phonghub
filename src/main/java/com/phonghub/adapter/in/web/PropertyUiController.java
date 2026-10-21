package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
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
            model.addAttribute("rooms", roomPage.items());
            model.addAttribute("roomPage", roomPage);
            model.addAttribute("roomStatuses", RoomStatus.values());
            model.addAttribute("filterStatus", status);
            model.addAttribute("filterMinPrice", minPrice);
            model.addAttribute("filterMaxPrice", maxPrice);
            model.addAttribute("filterFloor", floor);
            model.addAttribute("roomFilterActive", isPresent(status) || isPresent(minPrice) || isPresent(maxPrice)
                || isPresent(floor));
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
                ? "Đã cập nhật. Thay đổi tên hoặc địa chỉ cần được quản trị viên duyệt lại."
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
        RedirectAttributes redirectAttributes
    ) {
        try {
            Room room = roomUseCase.createRoom(new RoomUseCase.CreateRoomCommand(
                id, roomNumber, floor, areaSqm, basePrice, maxOccupants
            ));
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo phòng '" + room.getRoomNumber() + "'.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + id;
    }
}
