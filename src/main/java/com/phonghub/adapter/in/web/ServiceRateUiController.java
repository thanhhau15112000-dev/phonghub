package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.ChargeType;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Thêm và sửa đơn giá điện nước, dịch vụ ngay trên trang nhà trọ (#366); quyền do UtilityService kiểm. */
@Controller
@RequestMapping("/properties/{propertyId}/service-rates")
public class ServiceRateUiController {

    private static final String PRICE_INVALID = "Đơn giá phải là số không âm.";

    private final UtilityUseCase utilityUseCase;

    public ServiceRateUiController(UtilityUseCase utilityUseCase) {
        this.utilityUseCase = utilityUseCase;
    }

    @PostMapping
    public String create(
        @PathVariable UUID propertyId,
        @RequestParam(defaultValue = "") String serviceName,
        @RequestParam(required = false) ChargeType chargeType,
        @RequestParam(required = false) String pricePerUnit,
        RedirectAttributes redirectAttributes
    ) {
        try {
            BigDecimal price = validate(serviceName, chargeType, pricePerUnit);
            utilityUseCase.createRate(new UtilityUseCase.RateCommand(propertyId, serviceName.trim(), chargeType,
                price));
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm đơn giá \"" + serviceName.trim() + "\".");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "#serviceRates";
    }

    @PostMapping("/{rateId}")
    public String update(
        @PathVariable UUID propertyId,
        @PathVariable UUID rateId,
        @RequestParam(defaultValue = "") String serviceName,
        @RequestParam(required = false) ChargeType chargeType,
        @RequestParam(required = false) String pricePerUnit,
        RedirectAttributes redirectAttributes
    ) {
        try {
            BigDecimal price = validate(serviceName, chargeType, pricePerUnit);
            utilityUseCase.updateRate(rateId, new UtilityUseCase.RateCommand(propertyId, serviceName.trim(), chargeType,
                price));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật đơn giá \"" + serviceName.trim() + "\".");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/properties/" + propertyId + "#serviceRates";
    }

    private static BigDecimal validate(String serviceName, ChargeType chargeType, String pricePerUnit) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập tên dịch vụ.");
        }
        if (chargeType == null) {
            throw new IllegalArgumentException("Vui lòng chọn cách tính.");
        }
        BigDecimal price = FormNumbers.parseOptional(pricePerUnit, PRICE_INVALID);
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException(PRICE_INVALID);
        }
        return price;
    }
}
