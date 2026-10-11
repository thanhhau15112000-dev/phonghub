package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.MeterSubmissionUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
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

/** Người thuê gửi ảnh + số đồng hồ; owner/staff duyệt hoặc từ chối. */
@Controller
@RequestMapping("/rooms/{roomId}/meter-submissions")
public class MeterSubmissionUiController {

    private final MeterSubmissionUseCase useCase;
    private final RoomUseCase roomUseCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;

    public MeterSubmissionUiController(
        MeterSubmissionUseCase useCase,
        RoomUseCase roomUseCase,
        PropertyUseCase propertyUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.useCase = useCase;
        this.roomUseCase = roomUseCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
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
            model.addAttribute("isTenant", user != null && user.role() == UserRole.TENANT);
            model.addAttribute("meterTypes", MeterType.values());
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

    @GetMapping("/{submissionId}/photo")
    public ResponseEntity<Void> photo(@PathVariable UUID roomId, @PathVariable UUID submissionId) {
        URI target = useCase.photoSignedUrl(submissionId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
