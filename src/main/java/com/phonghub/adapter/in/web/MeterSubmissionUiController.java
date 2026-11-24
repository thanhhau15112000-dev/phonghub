package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.MeterSubmissionUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Người thuê gửi ảnh + số đồng hồ; owner/staff duyệt hoặc từ chối. Từ #366 trang còn hiện mốc chỉ số kỳ trước, đọc số
 * từ ảnh để tự điền, cho owner/staff ghi chỉ số trực tiếp và xem các chỉ số đã chốt.
 */
@Controller
@RequestMapping("/rooms/{roomId}/meter-submissions")
public class MeterSubmissionUiController {

    private final MeterSubmissionUseCase useCase;
    private final RoomUseCase roomUseCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;
    private final UtilityUseCase utilityUseCase;

    public MeterSubmissionUiController(
        MeterSubmissionUseCase useCase,
        RoomUseCase roomUseCase,
        PropertyUseCase propertyUseCase,
        CurrentUserPort currentUserPort,
        UtilityUseCase utilityUseCase
    ) {
        this.useCase = useCase;
        this.roomUseCase = roomUseCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
        this.utilityUseCase = utilityUseCase;
    }

    @GetMapping
    public String page(@PathVariable UUID roomId, Model model, RedirectAttributes redirectAttributes) {
        try {
            CurrentUser user = currentUserPort.getCurrentUser();
            Room room = roomUseCase.getRoom(roomId);
            model.addAttribute("submissions", useCase.listForRoom(roomId));
            model.addAttribute("room", room);
            model.addAttribute("property", propertyUseCase.getProperty(room.getPropertyId()));
            model.addAttribute("currentUser", user);
            boolean tenant = user != null && user.role() == UserRole.TENANT;
            model.addAttribute("isTenant", tenant);
            model.addAttribute("meterTypes", MeterType.values());
            // Mốc chỉ số kỳ trước theo tên loại đồng hồ (ELECTRIC/WATER) cho template và script tự tính tiêu thụ
            Map<String, UtilityUseCase.PreviousReading> previous = new LinkedHashMap<>();
            utilityUseCase.previousReadings(roomId).forEach((type, reading) -> previous.put(type.name(), reading));
            model.addAttribute("previousReadings", previous);
            model.addAttribute("readings", utilityUseCase.listReadings(roomId));
            model.addAttribute("canRecordReading", !tenant && user != null && user.role() != UserRole.ADMIN);
            model.addAttribute("today", LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
            return "rooms/meter-submissions";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/rooms/" + roomId;
        }
    }

    @PostMapping
    public String submit(
        @PathVariable UUID roomId,
        @RequestParam MeterType meterType,
        @RequestParam BigDecimal declaredValue,
        @RequestParam("file") MultipartFile file,
        RedirectAttributes redirectAttributes
    ) {
        try {
            useCase.submit(roomId, meterType, declaredValue, file.getContentType(), file.getBytes());
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi chỉ số, chờ chủ trọ duyệt.");
        } catch (DomainException | IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + roomId + "/meter-submissions";
    }

    @PostMapping("/{submissionId}/accept")
    public String accept(
        @PathVariable UUID roomId,
        @PathVariable UUID submissionId,
        @RequestParam(required = false) BigDecimal finalValue,
        @RequestParam(required = false) BigDecimal baselineValue,
        @RequestParam(defaultValue = "false") boolean acknowledgeMismatch,
        RedirectAttributes redirectAttributes
    ) {
        try {
            useCase.accept(submissionId, new MeterSubmissionUseCase.AcceptCommand(
                finalValue, baselineValue, acknowledgeMismatch));
            redirectAttributes.addFlashAttribute("successMessage", "Đã duyệt và ghi chỉ số chính thức.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + roomId + "/meter-submissions";
    }

    @PostMapping("/{submissionId}/reject")
    public String reject(
        @PathVariable UUID roomId,
        @PathVariable UUID submissionId,
        @RequestParam String reason,
        RedirectAttributes redirectAttributes
    ) {
        try {
            useCase.reject(submissionId, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối lượt gửi chỉ số.");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + roomId + "/meter-submissions";
    }

    /** Đọc số từ ảnh đồng hồ để tự điền vào form (#366); không lưu ảnh hay chỉ số. */
    @PostMapping("/ocr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> readPhoto(
        @PathVariable UUID roomId,
        @RequestParam MeterType meterType,
        @RequestParam("file") MultipartFile file
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            UtilityUseCase.OcrSuggestion suggestion = utilityUseCase.readMeterPhoto(roomId, meterType,
                file.getContentType(), file.getBytes());
            body.put("value", suggestion.value() == null ? null : suggestion.value().stripTrailingZeros().toPlainString());
            body.put("manual", suggestion.requiresManualEntry());
            body.put("message", suggestion.requiresManualEntry()
                ? "Không đọc chắc được số trên ảnh, hãy tự nhập số."
                : "Đã đọc số từ ảnh, hãy kiểm tra lại trước khi gửi.");
            return ResponseEntity.ok(body);
        } catch (UnauthorizedPropertyAccessException ex) {
            body.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        } catch (DomainException | IllegalArgumentException | IOException ex) {
            body.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    /** Chủ trọ/nhân viên ghi chỉ số trực tiếp (#366); mốc kỳ trước hệ thống tự lấy như API ghi chỉ số. */
    @PostMapping("/readings")
    public String recordReading(
        @PathVariable UUID roomId,
        @RequestParam MeterType meterType,
        @RequestParam(required = false) String currentValue,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readingDate,
        @RequestParam(required = false) String baselineValue,
        @RequestParam(value = "file", required = false) MultipartFile file,
        RedirectAttributes redirectAttributes
    ) {
        try {
            BigDecimal current = FormNumbers.parseOptional(currentValue, "Chỉ số mới phải là số.");
            if (current == null) {
                throw new IllegalArgumentException("Vui lòng nhập chỉ số mới trên đồng hồ.");
            }
            BigDecimal baseline = FormNumbers.parseOptional(baselineValue, "Chỉ số gốc phải là số.");
            var reading = utilityUseCase.recordReading(new UtilityUseCase.RecordReadingCommand(roomId, meterType,
                current, readingDate, baseline, null, null, null));
            if (file != null && !file.isEmpty()) {
                try {
                    utilityUseCase.attachPhoto(reading.id(), file.getContentType(), file.getBytes());
                } catch (DomainException | IllegalArgumentException | IOException ex) {
                    // Chỉ số đã lưu; chỉ báo phần ảnh để người dùng không ghi lại lần nữa
                    redirectAttributes.addFlashAttribute("errorMessage",
                        "Đã ghi chỉ số nhưng chưa lưu được ảnh: " + ex.getMessage());
                }
            }
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi chỉ số "
                + (meterType == MeterType.ELECTRIC ? "điện" : "nước") + " kỳ " + reading.period()
                + ": tiêu thụ " + reading.consumption().stripTrailingZeros().toPlainString()
                + (meterType == MeterType.ELECTRIC ? " kWh." : " m³."));
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/rooms/" + roomId + "/meter-submissions";
    }

    @GetMapping("/{submissionId}/photo")
    public ResponseEntity<Void> photo(@PathVariable UUID roomId, @PathVariable UUID submissionId) {
        URI target = useCase.photoSignedUrl(submissionId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
