package com.phonghub.application.port.in;

import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Tenant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractUseCase {
    Contract createContract(CreateContractCommand command);
    Contract activateContract(UUID contractId);
    Contract terminateContract(UUID contractId, boolean requiresMaintenance);

    /**
     * Chấm dứt hợp đồng ngay, không qua biên bản check-out (người thuê bỏ đi, vi phạm...): chỉ chủ nhà trọ hoặc quản trị
     * viên, bắt buộc có lý do (ghi vào nhật ký). Trả phòng thông thường dùng quy trình check-out.
     */
    Contract terminateEarly(UUID contractId, boolean requiresMaintenance, String reason);

    /**
     * Gia hạn hợp đồng người thuê còn ở: dời ngày kết thúc, có thể đổi tiền thuê cho các kỳ phát hành sau. Hợp đồng đã
     * hết hạn chưa trả phòng trở lại hiệu lực. {@code expectedVersion} lệch thì 409.
     */
    Contract renewContract(RenewContractCommand command);

    record RenewContractCommand(UUID contractId, LocalDate newEndDate, BigDecimal newRentAmount, Long expectedVersion) {
        public RenewContractCommand {
            if (contractId == null) {
                throw new IllegalArgumentException("Contract id is required");
            }
        }
    }

    /**
     * Sửa điều khoản hợp đồng nháp (#89): tiền cọc, tiền thuê, ngày bắt đầu/kết thúc, ngày thanh toán. Hợp đồng không còn
     * nháp thì 409; {@code expectedVersion} lệch (người khác vừa sửa) cũng 409.
     */
    Contract updateDraftContract(UpdateDraftContractCommand command);

    /** Hủy hợp đồng nháp (#89): chuyển CANCELLED, không xóa cứng. Hợp đồng không còn nháp thì 409. */
    Contract cancelDraftContract(UUID contractId);

    record UpdateDraftContractCommand(
        UUID contractId,
        java.math.BigDecimal depositAmount,
        java.math.BigDecimal rentAmount,
        java.time.LocalDate startDate,
        java.time.LocalDate endDate,
        int paymentDay,
        Long expectedVersion
    ) {
        public UpdateDraftContractCommand {
            if (contractId == null) {
                throw new IllegalArgumentException("Contract id is required");
            }
        }
    }
    Contract getContract(UUID contractId);
    List<Contract> listContractsForProperty(UUID propertyId);
    /** Active tenant accounts that may be selected for a contract in this property. */
    List<Tenant> listAvailablePrimaryTenants(UUID propertyId, String query);
    Optional<Contract> getActiveContractForTenant(UUID tenantUserId);
    List<Contract> listContractsForTenant(UUID tenantUserId);
    AddOccupantResult addOccupant(AddOccupantCommand command);
    /** Existing profiles visible in the property's tenant list that are not currently residing in a contract. */
    List<Tenant> listAvailableOccupants(UUID contractId);
    List<ContractOccupant> addExistingOccupants(AddExistingOccupantsCommand command);
    ContractOccupant checkOutOccupant(CheckOutOccupantCommand command);

    /**
     * Người ở cùng vừa thêm. Khi có tạo tài khoản mới, kèm tên đăng nhập và mật khẩu tạm (chỉ trả về một lần, người dùng
     * phải đổi ở lần đăng nhập đầu); liên kết với tài khoản người thuê có sẵn thì chỉ có tên đăng nhập.
     */
    record AddOccupantResult(ContractOccupant occupant, String accountUsername, String temporaryPassword) {
        public static AddOccupantResult withoutAccount(ContractOccupant occupant) {
            return new AddOccupantResult(occupant, null, null);
        }
    }

    record CheckOutOccupantCommand(
        UUID contractId,
        UUID tenantId,
        LocalDate checkOutDate
    ) {
        public CheckOutOccupantCommand {
            if (contractId == null) {
                throw new IllegalArgumentException("Contract ID cannot be null");
            }
            if (tenantId == null) {
                throw new IllegalArgumentException("Tenant ID cannot be null");
            }
            if (checkOutDate == null) {
                checkOutDate = LocalDate.now();
            }
        }
    }

    record AddOccupantCommand(
        UUID contractId,
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress,
        LocalDate checkInDate,
        boolean createAccount
    ) {
        public AddOccupantCommand {
            if (contractId == null) {
                throw new IllegalArgumentException("Contract ID cannot be null");
            }
            if (fullName == null || fullName.isBlank()) {
                throw new IllegalArgumentException("Họ và tên không được để trống");
            }
            if (identityCardNumber == null || identityCardNumber.isBlank()) {
                throw new IllegalArgumentException("Số CCCD không được để trống");
            }
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException("Số điện thoại không được để trống");
            }
            if (checkInDate == null) {
                checkInDate = LocalDate.now();
            }
        }
    }

    record AddExistingOccupantsCommand(UUID contractId, List<UUID> tenantIds, LocalDate checkInDate) {
        public AddExistingOccupantsCommand {
            if (contractId == null) {
                throw new IllegalArgumentException("Contract ID cannot be null");
            }
            if (tenantIds == null || tenantIds.isEmpty()) {
                throw new IllegalArgumentException("Chọn ít nhất một người thuê.");
            }
            if (tenantIds.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Tenant ID cannot be null");
            }
            if (tenantIds.stream().distinct().count() != tenantIds.size()) {
                throw new IllegalArgumentException("Không thể chọn trùng hồ sơ người thuê.");
            }
            tenantIds = List.copyOf(tenantIds);
            if (checkInDate == null) {
                checkInDate = LocalDate.now();
            }
        }
    }

    record CreateContractCommand(
        UUID propertyId,
        UUID roomId,
        UUID primaryTenantId,
        BigDecimal depositAmount,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        int paymentDay
    ) {
        public CreateContractCommand {
            if (propertyId == null) {
                throw new IllegalArgumentException("Property id cannot be null");
            }
            if (roomId == null) {
                throw new IllegalArgumentException("Room id cannot be null");
            }
            if (primaryTenantId == null) {
                throw new IllegalArgumentException("Cần chọn hồ sơ người thuê đã có tài khoản.");
            }
            if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Tiền cọc không được âm.");
            }
            if (rentAmount == null || rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Tiền thuê phải lớn hơn 0.");
            }
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("Ngày bắt đầu và ngày kết thúc không được để trống.");
            }
            if (!endDate.isAfter(startDate)) {
                throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
            }
            if (paymentDay < 1 || paymentDay > 31) {
                throw new IllegalArgumentException("Ngày thanh toán phải từ 1 đến 31.");
            }
        }
    }
}
