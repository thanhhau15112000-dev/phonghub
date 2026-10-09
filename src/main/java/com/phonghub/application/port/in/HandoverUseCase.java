package com.phonghub.application.port.in;

import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.PropertyHouseRules;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Check-in diễn ra sau khi hợp đồng đã ACTIVE; duyệt biên bản không đổi trạng thái hợp đồng hay phòng. */
public interface HandoverUseCase {

    Handover create(HandoverCommand command);

    Handover update(HandoverCommand command);

    HandoverPhoto addPhoto(UUID contractId, HandoverPhotoKind kind, String contentType, byte[] content);

    void removePhoto(UUID contractId, UUID photoId);

    /** Gửi cho khách: chụp tài sản của phòng, nội quy và hash điều khoản; DRAFT thành PENDING_TENANT. */
    Handover send(UUID contractId);

    /** Người thuê chính xác nhận; cần đã tải giấy tờ CCCD. */
    Handover confirm(UUID contractId);

    Handover approve(UUID contractId);

    Handover reject(UUID contractId, String reason);

    Handover reopen(UUID contractId);

    HandoverDetails get(UUID contractId);

    URI photoSignedUrl(UUID contractId, UUID photoId);

    PropertyHouseRules getHouseRules(UUID propertyId);

    PropertyHouseRules saveHouseRules(UUID propertyId, String content);

    record HandoverCommand(
        UUID contractId,
        BigDecimal initialElectricMeter,
        BigDecimal initialWaterMeter,
        String roomCondition,
        String keysProvided
    ) {}

    record HandoverDetails(Handover handover, List<HandoverPhoto> photos, List<HandoverItem> items) {}
}
