package com.phonghub.application.port.in;

import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.PropertyHouseRules;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Biên bản bàn giao của hợp đồng. Check-in được lập từ hợp đồng nháp; duyệt check-in sẽ kích hoạt hợp đồng.
 * Check-out cần check-in đã duyệt; duyệt check-out (owner chốt khấu trừ) kết thúc hợp đồng và trả phòng trong cùng
 * một transaction.
 */
public interface HandoverUseCase {

    /** Chuẩn bị biên bản check-in từ hiện trạng gần nhất; gọi lại sẽ trả biên bản đang có. */
    Handover prepareCheckIn(UUID contractId);

    Handover create(HandoverType type, HandoverCommand command);

    /** Thêm tài sản vào kho phòng và bản chụp check-in đang nháp. */
    HandoverItem addCheckInAsset(UUID contractId, NewAssetCommand command);

    Handover update(HandoverType type, HandoverCommand command);

    HandoverPhoto addPhoto(HandoverType type, UUID contractId, HandoverPhotoKind kind, String contentType,
                           byte[] content);

    void removePhoto(HandoverType type, UUID contractId, UUID photoId);

    /** Cập nhật tình trạng một tài sản và khoản đề xuất khấu trừ; chỉ dùng cho check-out khi còn DRAFT. */
    HandoverItem updateItem(HandoverType type, UUID contractId, UUID itemId, ItemCommand command);

    /** Gửi cho khách: DRAFT thành PENDING_TENANT (check-in chụp tài sản của phòng, nội quy và hash điều khoản). */
    Handover send(HandoverType type, UUID contractId);

    /** Người thuê chính xác nhận; check-in cần đã tải giấy tờ CCCD. */
    Handover confirm(HandoverType type, UUID contractId);

    /** Duyệt. Check-out chỉ owner/admin duyệt và chốt khoản khấu trừ (mặc định bằng tổng đề xuất). */
    Handover approve(HandoverType type, UUID contractId, BigDecimal finalDeduction);

    Handover reject(HandoverType type, UUID contractId, String reason);

    Handover reopen(HandoverType type, UUID contractId);

    HandoverDetails get(HandoverType type, UUID contractId);

    URI photoSignedUrl(HandoverType type, UUID contractId, UUID photoId);

    /** Nội dung ảnh, cùng kiểm tra quyền như {@link #photoSignedUrl}; dùng khi kho lưu trữ không có URL cho trình duyệt. */
    PhotoFile photoFile(HandoverType type, UUID contractId, UUID photoId);

    PropertyHouseRules getHouseRules(UUID propertyId);

    /** Read house rules through a contract already visible to the current tenant, including a DRAFT check-in. */
    PropertyHouseRules getHouseRulesForContract(UUID contractId);

    PropertyHouseRules saveHouseRules(UUID propertyId, String content);

    record HandoverCommand(
        UUID contractId,
        BigDecimal initialElectricMeter,
        BigDecimal initialWaterMeter,
        String roomCondition,
        String keysProvided
    ) {}

    record ItemCommand(AssetStatus status, String conditionNote, BigDecimal deductionAmount) {}

    record NewAssetCommand(String name, int quantity, AssetStatus status, String note, BigDecimal estimatedValue) {}

    record PhotoFile(String contentType, byte[] content) {}

    record HandoverDetails(Handover handover, List<HandoverPhoto> photos, List<HandoverItem> items) {
        /** Tổng khoản khấu trừ do nhân viên đề xuất trên từng tài sản. */
        public BigDecimal proposedDeduction() {
            return items.stream().map(HandoverItem::deductionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
