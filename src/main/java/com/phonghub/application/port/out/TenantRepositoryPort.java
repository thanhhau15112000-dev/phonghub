package com.phonghub.application.port.out;

import com.phonghub.domain.model.Tenant;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TenantRepositoryPort {
    Tenant save(Tenant tenant);
    Optional<Tenant> findById(UUID id);
    /** Serialize residency changes for an existing tenant within the caller's transaction. */
    default Optional<Tenant> findByIdForUpdate(UUID id) {
        return findById(id);
    }
    Optional<Tenant> findByUserId(UUID userId);
    Optional<Tenant> findByIdentityCardNumber(String idCard);
    List<Tenant> findAll();

    /** Account-backed profiles without any direct property assignment, used for admin-created unassigned tenants. */
    List<Tenant> findAccountProfilesWithoutPropertyLinks();

    /** Gắn tenant với nhà trọ (idempotent); không thay đổi trạng thái ẩn nếu đã có. */
    void linkToProperty(UUID tenantId, UUID propertyId, UUID actorId);

    /** Các nhà trọ mà tenant được gắn trực tiếp (kể cả đang ẩn), không tính nhà trọ chỉ có hợp đồng. */
    Set<UUID> findLinkedPropertyIds(UUID tenantId);

    /** Các liên kết của một nhà trọ; {@code hiddenAt} null nghĩa là đang hiển thị. */
    List<PropertyLink> findLinks(UUID propertyId);

    /** Ẩn ({@code hiddenAt != null}) hoặc khôi phục tenant ở một nhà trọ; tạo liên kết nếu chưa có. */
    void setHidden(UUID tenantId, UUID propertyId, Instant hiddenAt, UUID actorId);

    List<Tenant> findAllByIds(Collection<UUID> ids);

    record PropertyLink(UUID tenantId, Instant hiddenAt) {}
}
