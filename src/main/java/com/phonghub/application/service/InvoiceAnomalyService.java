package com.phonghub.application.service;

import com.phonghub.application.port.in.InvoiceAnomalyUseCase;
import com.phonghub.application.port.in.PaymentReconciliationUseCase;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationFilter;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationRow;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationStatus;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.DuplicateRentInvoices;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.MissingReading;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.UtilityReading;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quy tắc phát hiện bất thường (#156). Số liệu đến từ {@link InvoiceAnomalyQueryPort} và đối soát SePay có sẵn; ở đây
 * chỉ so sánh và gắn nhãn, không gọi AI.
 */
public class InvoiceAnomalyService implements InvoiceAnomalyUseCase {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InvoiceAnomalyQueryPort query;
    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final PaymentReconciliationUseCase reconciliation;
    private final Clock clock;

    public InvoiceAnomalyService(
        InvoiceAnomalyQueryPort query,
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        PaymentReconciliationUseCase reconciliation,
        Clock clock
    ) {
        this.query = query;
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.reconciliation = reconciliation;
        this.clock = clock;
    }

    @Override
    public AnomalyReport analyze(UUID propertyId, YearMonth period) {
        if (period == null) {
            throw new IllegalArgumentException("Kỳ phân tích không được để trống.");
        }
        List<UUID> scope = ownerScope(propertyId);
        List<Anomaly> found = new ArrayList<>();
        if (!scope.isEmpty()) {
            found.addAll(utilityRules(
                query.utilityReadings(scope, period.minusMonths(BASELINE_PERIODS), period), period));
            query.missingReadings(scope, period).forEach(m -> found.add(missing(m, period)));
            query.duplicateRentInvoices(scope, period).forEach(d -> found.add(duplicate(d)));
            found.addAll(stalePaymentReviews(propertyId));
        }
        found.sort(Comparator.comparing(Anomaly::severity)
            .thenComparing(a -> a.roomNumber() == null ? "" : a.roomNumber())
            .thenComparing(a -> a.type().ordinal()));
        return new AnomalyReport(period, propertyId, List.copyOf(found));
    }

    /** Quy tắc điện/nước: tăng vọt, chỉ số không đổi, chỉ số lùi. Hàm tĩnh để test không cần dữ liệu thật. */
    static List<Anomaly> utilityRules(List<UtilityReading> readings, YearMonth period) {
        Map<String, List<UtilityReading>> byMeter = readings.stream().collect(Collectors.groupingBy(
            r -> r.roomId() + "/" + r.type(), LinkedHashMap::new, Collectors.toList()));
        List<Anomaly> result = new ArrayList<>();
        for (List<UtilityReading> meter : byMeter.values()) {
            UtilityReading current = meter.stream().filter(r -> r.period().equals(period)).findFirst().orElse(null);
            if (current == null || current.replaced()) {
                continue; // Đã thay công tơ: số đầu/cuối không so sánh được với kỳ trước.
            }
            List<UtilityReading> prior = meter.stream().filter(r -> r.period().isBefore(period))
                .sorted(Comparator.comparing(UtilityReading::period).reversed())
                .limit(BASELINE_PERIODS).toList();
            if (current.currentValue().compareTo(current.previousValue()) < 0
                || (!prior.isEmpty() && current.currentValue().compareTo(prior.get(0).currentValue()) < 0)) {
                result.add(backward(current, prior.isEmpty() ? null : prior.get(0)));
                continue;
            }
            BigDecimal baseline = average(prior);
            if (baseline == null || baseline.signum() <= 0) {
                continue; // Chưa có lịch sử dùng điện/nước để so sánh.
            }
            BigDecimal limit = baseline.multiply(HUNDRED.add(BigDecimal.valueOf(SPIKE_PERCENT))).divide(HUNDRED);
            if (current.consumption().signum() == 0) {
                result.add(zero(current, baseline, prior.size()));
            } else if (current.consumption().compareTo(limit) > 0) {
                result.add(spike(current, baseline, prior.size()));
            }
        }
        return result;
    }

    private static BigDecimal average(List<UtilityReading> prior) {
        if (prior.isEmpty()) {
            return null;
        }
        BigDecimal sum = prior.stream().map(UtilityReading::consumption).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(prior.size()), 2, RoundingMode.HALF_UP);
    }

    private static Anomaly spike(UtilityReading r, BigDecimal baseline, int priorCount) {
        BigDecimal pct = r.consumption().subtract(baseline).multiply(HUNDRED)
            .divide(baseline, 0, RoundingMode.HALF_UP);
        Map<String, String> evidence = evidence("Kỳ này", num(r.consumption()) + " " + unit(r.type()),
            "Trung bình " + priorCount + " kỳ trước", num(baseline) + " " + unit(r.type()),
            "Tăng", num(pct) + "%");
        return new Anomaly(Type.UTILITY_SPIKE, Severity.MEDIUM, r.propertyId(), r.roomId(), r.roomNumber(),
            "Phòng " + r.roomNumber() + ": " + name(r.type()) + " tăng " + num(pct) + "% so với trung bình "
                + priorCount + " kỳ trước.",
            evidence, "Đối chiếu chỉ số công tơ với ảnh chụp và hỏi người thuê trước khi gửi hóa đơn.");
    }

    private static Anomaly zero(UtilityReading r, BigDecimal baseline, int priorCount) {
        return new Anomaly(Type.METER_ZERO, Severity.LOW, r.propertyId(), r.roomId(), r.roomNumber(),
            "Phòng " + r.roomNumber() + ": chỉ số " + name(r.type()) + " không đổi trong kỳ.",
            evidence("Kỳ này", "0 " + unit(r.type()),
                "Trung bình " + priorCount + " kỳ trước", num(baseline) + " " + unit(r.type())),
            "Kiểm tra phòng có người ở không, hoặc công tơ có hỏng hay chưa được đọc.");
    }

    private static Anomaly backward(UtilityReading r, UtilityReading previousPeriod) {
        Map<String, String> evidence = evidence("Chỉ số đầu kỳ", num(r.previousValue()),
            "Chỉ số cuối kỳ", num(r.currentValue()));
        if (previousPeriod != null) {
            evidence.put("Chỉ số cuối kỳ trước", num(previousPeriod.currentValue()));
        }
        return new Anomaly(Type.METER_BACKWARD, Severity.HIGH, r.propertyId(), r.roomId(), r.roomNumber(),
            "Phòng " + r.roomNumber() + ": chỉ số " + name(r.type()) + " thấp hơn chỉ số trước đó.",
            evidence, "Nhập lại chỉ số; nếu đã thay công tơ thì ghi nhận thay công tơ.");
    }

    private static Anomaly missing(MissingReading m, YearMonth period) {
        return new Anomaly(Type.MISSING_READING, Severity.MEDIUM, m.propertyId(), m.roomId(), m.roomNumber(),
            "Phòng " + m.roomNumber() + ": chưa có chỉ số " + name(m.type()) + " tháng " + period + ".",
            evidence("Kỳ", period.toString(), "Loại công tơ", name(m.type())),
            "Nhập chỉ số trước khi lập hóa đơn tháng, nếu không hóa đơn sẽ thiếu tiền " + name(m.type()) + ".");
    }

    private static Anomaly duplicate(DuplicateRentInvoices d) {
        return new Anomaly(Type.DUPLICATE_RENT_INVOICE, Severity.HIGH, d.propertyId(), d.roomId(), d.roomNumber(),
            "Phòng " + d.roomNumber() + ": có " + d.invoiceIds().size() + " hóa đơn tiền thuê tháng " + d.period()
                + " từ nhiều hợp đồng.",
            evidence("Kỳ", d.period().toString(), "Số hóa đơn", String.valueOf(d.invoiceIds().size()),
                "Mã hóa đơn", d.invoiceIds().stream().map(UUID::toString).collect(Collectors.joining(", "))),
            "Kiểm tra hợp đồng của phòng có bị chồng thời gian không và hủy hóa đơn thừa.");
    }

    private List<Anomaly> stalePaymentReviews(UUID propertyId) {
        List<ReconciliationRow> rows = reconciliation.listTransactions(
            new ReconciliationFilter(propertyId, ReconciliationStatus.NEEDS_REVIEW, null, null)).rows();
        List<Anomaly> result = new ArrayList<>();
        for (ReconciliationRow row : rows) {
            if (row.receivedAt() == null) {
                continue;
            }
            long days = Duration.between(row.receivedAt(), clock.instant()).toDays();
            if (days < STALE_REVIEW_DAYS) {
                continue;
            }
            result.add(new Anomaly(Type.STALE_PAYMENT_REVIEW, Severity.MEDIUM, row.propertyId(), null,
                row.roomNumber(),
                "Giao dịch chuyển khoản" + (row.roomNumber() == null ? "" : " phòng " + row.roomNumber())
                    + " cần xem xét đã " + days + " ngày chưa xử lý.",
                evidence("Mã giao dịch", row.sepayId() == null ? "-" : String.valueOf(row.sepayId()),
                    "Số ngày chờ", String.valueOf(days),
                    "Lý do", row.reviewReason() == null ? "-" : row.reviewReason().name()),
                "Mở trang Giao dịch, xử lý tiền dư hoặc hoàn tiền cho người thuê."));
        }
        return result;
    }

    /** Chỉ chủ trọ, và chỉ nhà của mình (cùng quy tắc với thống kê tài chính, #163). */
    private List<UUID> ownerScope(UUID propertyId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.ADMIN) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.ADMIN_FINANCE_DENIED);
        }
        if (user.role() != UserRole.OWNER) {
            throw new UnauthorizedPropertyAccessException("Chỉ chủ nhà trọ được xem phân tích hóa đơn.");
        }
        if (propertyId != null) {
            authorizationService.assertOwnerOrAdmin(user, propertyId);
            return List.of(propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + propertyId)).id());
        }
        return propertyRepository.findByOwnerId(user.id()).stream()
            .filter(p -> !p.isArchived()).map(Property::id).toList();
    }

    private static Map<String, String> evidence(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String num(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static String name(MeterType type) {
        return type == MeterType.ELECTRIC ? "điện" : "nước";
    }

    private static String unit(MeterType type) {
        return type == MeterType.ELECTRIC ? "kWh" : "m³";
    }
}
