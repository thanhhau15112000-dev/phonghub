package com.phonghub.application.service;

import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tính các dòng điện, nước và dịch vụ cho hóa đơn tháng từ chỉ số công tơ và bảng giá của nhà trọ.
 * Điện/nước tính theo chỉ số của đúng kỳ hóa đơn; dịch vụ cố định (theo phòng, theo người) chỉ tính lúc phát hành
 * để đơn giá thêm sau đó không đổi hóa đơn người thuê đã nhận.
 */
class UtilityBilling {

    private static final UtilityBilling NONE = new UtilityBilling(null, null);

    private final MeterReadingRepositoryPort readingRepository;
    private final ServiceRateRepositoryPort rateRepository;

    UtilityBilling(MeterReadingRepositoryPort readingRepository, ServiceRateRepositoryPort rateRepository) {
        this.readingRepository = readingRepository;
        this.rateRepository = rateRepository;
    }

    /** Không tính điện nước (dùng khi không cấu hình kho chỉ số). */
    static UtilityBilling none() {
        return NONE;
    }

    private boolean disabled() {
        return readingRepository == null || rateRepository == null;
    }

    /** Các dòng tính được cho kỳ {@code period}; {@code includeFixed} chỉ đúng lúc vừa phát hành hóa đơn. */
    List<InvoiceItem> chargesFor(Contract contract, YearMonth period, boolean includeFixed) {
        List<InvoiceItem> charges = new ArrayList<>();
        if (disabled()) {
            return charges;
        }
        for (ServiceRate rate : rateRepository.findByPropertyId(contract.getPropertyId())) {
            switch (rate.chargeType()) {
                case PER_KWH -> meterCharge(contract, period, rate, MeterType.ELECTRIC, "kWh", charges);
                case PER_M3 -> meterCharge(contract, period, rate, MeterType.WATER, "m3", charges);
                case PER_ROOM_FLAT -> {
                    if (includeFixed) {
                        fixedCharge(rate, period, BigDecimal.ONE, charges);
                    }
                }
                case PER_PERSON -> {
                    if (includeFixed) {
                        fixedCharge(rate, period, BigDecimal.valueOf(residents(contract)), charges);
                    }
                }
            }
        }
        return charges;
    }

    /** Unbilled meter consumption captured at early termination; fixed monthly services are not repeated. */
    List<InvoiceItem> cancellationCharges(Contract contract, LocalDate finalReadingDate, List<Invoice> invoices) {
        List<InvoiceItem> charges = new ArrayList<>();
        if (disabled() || finalReadingDate == null) {
            return charges;
        }
        List<ServiceRate> rates = rateRepository.findByPropertyId(contract.getPropertyId());
        cancellationMeterCharges(contract, finalReadingDate, invoices, MeterType.ELECTRIC, ChargeType.PER_KWH,
            "kWh", rates, charges);
        cancellationMeterCharges(contract, finalReadingDate, invoices, MeterType.WATER, ChargeType.PER_M3,
            "m3", rates, charges);
        return charges;
    }

    private void cancellationMeterCharges(
        Contract contract, LocalDate finalReadingDate, List<Invoice> invoices, MeterType meterType,
        ChargeType chargeType, String unit, List<ServiceRate> rates, List<InvoiceItem> out
    ) {
        Optional<MeterReading> reading = readingRepository.findLatest(contract.getRoomId(), meterType)
            .filter(found -> found.contractId() == null || found.contractId().equals(contract.getId()))
            .filter(found -> !found.readingDate().isBefore(contract.billingAnchorDate())
                && !found.readingDate().isAfter(finalReadingDate))
            .filter(found -> found.consumption().signum() > 0);
        if (reading.isEmpty()) {
            return;
        }
        MeterReading meter = reading.get();
        for (ServiceRate rate : rates) {
            if (rate.chargeType() != chargeType || rate.pricePerUnit().signum() <= 0) {
                continue;
            }
            boolean alreadyBilled = invoices.stream().flatMap(invoice -> invoice.getItems().stream())
                .anyMatch(item -> meter.id().equals(item.readingId()) && rate.id().equals(item.rateId()));
            if (!alreadyBilled) {
                String name = String.format("%s đến %s (%s %s)", rate.serviceName(),
                    finalReadingDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    meter.consumption().stripTrailingZeros().toPlainString(), unit);
                out.add(InvoiceItem.charge(name, meter.consumption(), rate.pricePerUnit(), meter.id(), rate.id()));
            }
        }
    }

    private void meterCharge(
        Contract contract, YearMonth period, ServiceRate rate, MeterType type, String unit, List<InvoiceItem> out
    ) {
        readingOf(contract, period, type)
            .filter(reading -> reading.consumption().signum() > 0 && rate.pricePerUnit().signum() > 0)
            .ifPresent(reading -> out.add(InvoiceItem.charge(
                String.format("%s tháng %02d/%d (%s %s)", rate.serviceName(), period.getMonthValue(), period.getYear(),
                    reading.consumption().stripTrailingZeros().toPlainString(), unit),
                reading.consumption(), rate.pricePerUnit(), reading.id(), rate.id())));
    }

    private static void fixedCharge(ServiceRate rate, YearMonth period, BigDecimal quantity, List<InvoiceItem> out) {
        if (rate.pricePerUnit().signum() > 0 && quantity.signum() > 0) {
            out.add(InvoiceItem.charge(String.format("%s tháng %02d/%d", rate.serviceName(), period.getMonthValue(),
                period.getYear()), quantity, rate.pricePerUnit(), null, rate.id()));
        }
    }

    /** Số người đang ở, tối thiểu 1 (người thuê chính luôn tính). */
    private static long residents(Contract contract) {
        long current = contract.getOccupants().stream().filter(o -> o.checkOutDate() == null).count();
        return Math.max(1, current);
    }

    /** Chỉ số của kỳ; bỏ chỉ số gắn với hợp đồng khác của cùng phòng (khách thuê trước). */
    private Optional<MeterReading> readingOf(Contract contract, YearMonth period, MeterType type) {
        return readingRepository.findByPeriod(contract.getRoomId(), type, period.getYear(), period.getMonthValue())
            .filter(reading -> reading.contractId() == null || reading.contractId().equals(contract.getId()));
    }

    /**
     * Cảnh báo cho người xem hóa đơn tiền thuê: thiếu chỉ số hoặc đơn giá nên chưa tính điện/nước. Chỉ cảnh báo,
     * không chặn hóa đơn tiền thuê.
     */
    List<String> warnings(Invoice invoice, Contract contract) {
        List<String> warnings = new ArrayList<>();
        if (disabled() || invoice.getType() != InvoiceType.RENT || invoice.isConsolidated()
            || invoice.getStatus() == InvoiceStatus.VOIDED || invoice.getStatus() == InvoiceStatus.DRAFT) {
            return warnings;
        }
        YearMonth period = YearMonth.of(invoice.getYear(), invoice.getMonth());
        List<ServiceRate> rates = rateRepository.findByPropertyId(contract.getPropertyId());
        warnFor(invoice, contract, period, rates, MeterType.ELECTRIC, ChargeType.PER_KWH, "điện", warnings);
        warnFor(invoice, contract, period, rates, MeterType.WATER, ChargeType.PER_M3, "nước", warnings);
        return warnings;
    }

    private void warnFor(
        Invoice invoice, Contract contract, YearMonth period, List<ServiceRate> rates, MeterType type,
        ChargeType chargeType, String label, List<String> out
    ) {
        List<ServiceRate> typeRates = rates.stream().filter(rate -> rate.chargeType() == chargeType).toList();
        Optional<MeterReading> reading = readingOf(contract, period, type);
        String when = String.format("%02d/%d", period.getMonthValue(), period.getYear());
        if (reading.isEmpty()) {
            if (!typeRates.isEmpty()) {
                out.add(String.format("Chưa có chỉ số %s kỳ %s nên hóa đơn chưa tính tiền %s.", label, when, label));
            }
            return;
        }
        if (reading.get().consumption().signum() == 0) {
            return;
        }
        if (typeRates.isEmpty()) {
            out.add(String.format("Nhà trọ chưa cấu hình đơn giá %s nên hóa đơn kỳ %s chưa tính tiền %s.",
                label, when, label));
        } else if (!invoice.canAddCharges() && typeRates.stream().anyMatch(rate -> rate.pricePerUnit().signum() > 0
            && !invoice.hasCharge(reading.get().id(), rate.id()))) {
            out.add(String.format(
                "Chỉ số %s kỳ %s được ghi sau khi hóa đơn đã có thanh toán nên chưa được tính vào hóa đơn.",
                label, when));
        }
    }
}
