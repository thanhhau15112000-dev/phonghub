package com.phonghub.application.service;

import com.phonghub.application.port.in.CheckOutSettlementUseCase;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.InvoicePaymentRecordRepositoryPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tính quyết toán tiền cọc từ biên bản check-out. Quyền xem do {@link ContractUseCase#getContract} (phạm vi hợp đồng)
 * và {@link InvoiceUseCase#listInvoicesForContract} (số liệu tiền) quyết định. Không ghi gì: chỉ để chủ trọ và người
 * thuê đối chiếu trước/sau khi duyệt trả phòng.
 */
public class CheckOutSettlementService implements CheckOutSettlementUseCase {

    private final ContractUseCase contractUseCase;
    private final InvoiceUseCase invoiceUseCase;
    private final HandoverRepositoryPort handoverRepository;
    private final HandoverItemRepositoryPort itemRepository;
    private final MeterReadingRepositoryPort readingRepository;
    private final ServiceRateRepositoryPort rateRepository;
    private final InvoicePaymentRecordRepositoryPort paymentRecordRepository;

    public CheckOutSettlementService(
        ContractUseCase contractUseCase,
        InvoiceUseCase invoiceUseCase,
        HandoverRepositoryPort handoverRepository,
        HandoverItemRepositoryPort itemRepository,
        MeterReadingRepositoryPort readingRepository,
        ServiceRateRepositoryPort rateRepository
    ) {
        this(contractUseCase, invoiceUseCase, handoverRepository, itemRepository, readingRepository, rateRepository, null);
    }

    public CheckOutSettlementService(
        ContractUseCase contractUseCase,
        InvoiceUseCase invoiceUseCase,
        HandoverRepositoryPort handoverRepository,
        HandoverItemRepositoryPort itemRepository,
        MeterReadingRepositoryPort readingRepository,
        ServiceRateRepositoryPort rateRepository,
        InvoicePaymentRecordRepositoryPort paymentRecordRepository
    ) {
        this.contractUseCase = contractUseCase;
        this.invoiceUseCase = invoiceUseCase;
        this.handoverRepository = handoverRepository;
        this.itemRepository = itemRepository;
        this.readingRepository = readingRepository;
        this.rateRepository = rateRepository;
        this.paymentRecordRepository = paymentRecordRepository;
    }

    @Override
    public Optional<Settlement> settlement(UUID contractId) {
        Contract contract = contractUseCase.getContract(contractId);
        Optional<Handover> checkOut = handoverRepository.find(contractId, HandoverType.CHECK_OUT);
        if (checkOut.isEmpty()) {
            return Optional.empty();
        }
        List<Invoice> invoices;
        try {
            invoices = invoiceUseCase.listInvoicesForContract(contractId);
        } catch (UnauthorizedPropertyAccessException ex) {
            // không xem được số liệu tiền (admin, kỹ thuật viên, nhân viên không có quyền)
            return Optional.empty();
        }

        Handover handover = checkOut.get();
        boolean deductionFinal = handover.finalDeduction() != null;
        BigDecimal deduction = deductionFinal ? handover.finalDeduction()
            : itemRepository.findByHandoverId(handover.id()).stream().map(HandoverItem::deductionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Invoice> unpaid = invoices.stream().filter(Invoice::isPayable)
            .filter(invoice -> invoice.remainingAmount().signum() > 0).toList();
        BigDecimal outstanding = unpaid.stream().map(Invoice::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<UtilityEstimate> utilities = new ArrayList<>();
        if (invoices.stream().noneMatch(invoice -> invoice.getType() == com.phonghub.domain.model.InvoiceType.CHECK_OUT
            && invoice.getStatus() != com.phonghub.domain.model.InvoiceStatus.VOIDED)) {
            List<ServiceRate> rates = rateRepository.findByPropertyId(contract.getPropertyId());
            estimate(contract, MeterType.ELECTRIC, handover.initialElectricMeter(), ChargeType.PER_KWH, rates)
                .ifPresent(utilities::add);
            estimate(contract, MeterType.WATER, handover.initialWaterMeter(), ChargeType.PER_M3, rates)
                .ifPresent(utilities::add);
        }

        BigDecimal depositOffset = paymentRecordRepository == null ? BigDecimal.ZERO
            : paymentRecordRepository.findByContractIds(List.of(contractId)).stream()
                .map(record -> record.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<OutstandingInvoice> openInvoices = unpaid.stream()
            .map(invoice -> new OutstandingInvoice(invoice.getId(), invoice.getType(), invoice.getMonth(),
                invoice.getYear(), invoice.remainingAmount(), invoice.getDueDate()))
            .toList();

        return Optional.of(new Settlement(contract.getDepositAmount(), deduction, deductionFinal, outstanding,
            unpaid.size(), List.copyOf(utilities), depositOffset, openInvoices));
    }

    /**
     * Tiêu thụ từ lần ghi chỉ số gần nhất của hợp đồng (chưa có thì từ chỉ số check-in) tới chỉ số trong biên bản check-out,
     * nhân tổng đơn giá cùng loại (như cách hóa đơn tháng cộng từng đơn giá). Chỉ số lùi (thay đồng hồ) thì bỏ qua.
     */
    private Optional<UtilityEstimate> estimate(
        Contract contract, MeterType type, BigDecimal finalValue, ChargeType chargeType, List<ServiceRate> rates
    ) {
        if (finalValue == null) {
            return Optional.empty();
        }
        BigDecimal fromValue = readingRepository.findLatest(contract.getRoomId(), type)
            .filter(reading -> reading.contractId() == null || contract.getId().equals(reading.contractId()))
            .map(MeterReading::currentValue)
            .or(() -> handoverRepository.find(contract.getId(), HandoverType.CHECK_IN)
                .map(checkIn -> type == MeterType.ELECTRIC ? checkIn.initialElectricMeter() : checkIn.initialWaterMeter()))
            .orElse(null);
        if (fromValue == null || finalValue.compareTo(fromValue) < 0) {
            return Optional.empty();
        }
        BigDecimal unitPrice = rates.stream().filter(rate -> rate.chargeType() == chargeType)
            .map(ServiceRate::pricePerUnit).filter(price -> price.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumption = finalValue.subtract(fromValue);
        return Optional.of(new UtilityEstimate(type, fromValue, finalValue, consumption, unitPrice,
            consumption.multiply(unitPrice).setScale(0, java.math.RoundingMode.HALF_UP)));
    }
}
