package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.model.PropertyApprovalStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Đọc cả giá trị cũ ('APPROVED') như {@link PropertyApprovalStatus#fromString}; null khi ghi thành VERIFIED. */
@Converter
class PropertyApprovalStatusConverter implements AttributeConverter<PropertyApprovalStatus, String> {

    @Override
    public String convertToDatabaseColumn(PropertyApprovalStatus status) {
        return (status != null ? status : PropertyApprovalStatus.VERIFIED).name();
    }

    @Override
    public PropertyApprovalStatus convertToEntityAttribute(String value) {
        return PropertyApprovalStatus.fromString(value);
    }
}
