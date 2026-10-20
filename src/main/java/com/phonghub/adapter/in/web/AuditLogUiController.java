package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.AuditLogUseCase;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.domain.exception.DomainException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang xem nhật ký kiểm toán: admin xem tất cả, owner chỉ xem nhà trọ của mình. */
@Controller
public class AuditLogUiController {

    static final List<String> TARGET_TYPES = List.of(
        "USER", "PROPERTY", "ROOM", "CONTRACT", "CONTRACT_OCCUPANT", "TENANT", "TENANT_DOCUMENT",
        "MAINTENANCE_TICKET", "ROOM_ASSET", "HANDOVER", "SERVICE_RATE", "INVOICE", "EXPORT");

    private final AuditLogUseCase auditLogUseCase;

    public AuditLogUiController(AuditLogUseCase auditLogUseCase) {
        this.auditLogUseCase = auditLogUseCase;
    }

    @GetMapping("/audit")
    public String list(
        @RequestParam(required = false) UUID propertyId,
        @RequestParam(required = false) String actorId,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "0") int page,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        try {
            UUID actor = parseUuid(actorId);
            LocalDate fromDate = parseDate(from);
            LocalDate toDate = parseDate(to);
            // Mã hành động lưu dạng chữ in hoa; gõ tay "room_updated" trên URL vẫn lọc được.
            String actionCode = action == null || action.isBlank() ? null : action.trim().toUpperCase(Locale.ROOT);
            List<AuditQueryPort.Entry> entries;
            if ((actorId != null && !actorId.isBlank() && actor == null)
                || (from != null && !from.isBlank() && fromDate == null)
                || (to != null && !to.isBlank() && toDate == null)) {
                model.addAttribute("errorMessage", "Bộ lọc không hợp lệ: kiểm tra mã người thực hiện và ngày (yyyy-MM-dd).");
                entries = List.of();
            } else {
                entries = auditLogUseCase.search(new AuditLogUseCase.SearchCommand(
                    propertyId, actor, targetType, actionCode, fromDate, toDate, page));
            }
            Map<UUID, List<AuditDetailsFormatter.Line>> detailLines = new HashMap<>();
            entries.forEach(e -> detailLines.put(e.id(), AuditDetailsFormatter.format(e.targetType(), e.details())));
            List<AuditLogUseCase.PropertyOption> properties = auditLogUseCase.selectableProperties();
            Map<UUID, String> propertyNames = new HashMap<>();
            properties.forEach(p -> propertyNames.put(p.id(), p.name()));
            model.addAttribute("entries", entries);
            model.addAttribute("detailLines", detailLines);
            model.addAttribute("propertyNames", propertyNames);
            model.addAttribute("properties", properties);
            model.addAttribute("targetTypes", TARGET_TYPES);
            model.addAttribute("propertyId", propertyId);
            model.addAttribute("actorId", actorId);
            model.addAttribute("targetType", targetType);
            model.addAttribute("action", actionCode);
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("page", Math.max(0, page));
            model.addAttribute("pageSize", AuditLogUseCase.PAGE_SIZE);
            return "audit/list";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/dashboard";
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
