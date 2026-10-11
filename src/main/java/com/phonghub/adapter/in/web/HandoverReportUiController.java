package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.HandoverReportUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.HandoverType;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Màn đối chiếu tài sản check-in / check-out và tải biên bản PDF đã duyệt. */
@Controller
@RequestMapping("/contracts/{contractId}/handover-report")
public class HandoverReportUiController {

    private final HandoverReportUseCase reportUseCase;
    private final ContractUseCase contractUseCase;
    private final PropertyUseCase propertyUseCase;
    private final CurrentUserPort currentUserPort;

    public HandoverReportUiController(
        HandoverReportUseCase reportUseCase,
        ContractUseCase contractUseCase,
        PropertyUseCase propertyUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.reportUseCase = reportUseCase;
        this.contractUseCase = contractUseCase;
        this.propertyUseCase = propertyUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String compare(@PathVariable UUID contractId, Model model, RedirectAttributes redirectAttributes) {
        try {
            Contract contract = contractUseCase.getContract(contractId);
            model.addAttribute("comparison", reportUseCase.compare(contractId));
            model.addAttribute("contract", contract);
            model.addAttribute("property", propertyUseCase.getProperty(contract.getPropertyId()));
            model.addAttribute("currentUser", currentUserPort.getCurrentUser());
            return "contracts/handover-compare";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/contracts/" + contractId;
        }
    }

    @GetMapping("/{kind:check-in|check-out}/pdf")
    public ResponseEntity<byte[]> pdf(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        RedirectAttributes redirectAttributes
    ) {
        HandoverType type = "check-out".equals(kind) ? HandoverType.CHECK_OUT : HandoverType.CHECK_IN;
        try {
            HandoverReportUseCase.PdfDocument document = reportUseCase.exportPdf(contractId, type);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(document.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(document.content());
        } catch (DomainException ex) {
            // Biên bản chưa duyệt hoặc không đủ quyền: quay về màn đối chiếu kèm thông báo.
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/contracts/" + contractId + "/handover-report").build();
        }
    }
}
