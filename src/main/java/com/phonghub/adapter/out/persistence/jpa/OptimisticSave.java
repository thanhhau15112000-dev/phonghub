package com.phonghub.adapter.out.persistence.jpa;

import com.phonghub.domain.exception.OptimisticLockConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Giữ hợp đồng khóa lạc quan của các port: mỗi lần lưu bản ghi đã có thì version tăng đúng 1, kể cả khi Hibernate
 * không thấy trường nào đổi (ví dụ chỉ danh sách người ở của hợp đồng thay đổi), và ghi đè bản mới hơn thì báo
 * {@link OptimisticLockConflictException}.
 */
final class OptimisticSave {

    private OptimisticSave() {
    }

    /**
     * Flush ngay; nếu bản ghi đã có ({@code versionBefore} khác null) mà flush không tăng version thì tăng ngay bằng
     * PESSIMISTIC_FORCE_INCREMENT (OPTIMISTIC_FORCE_INCREMENT chỉ tăng lúc commit nên không trả version mới được).
     */
    static <E> E saveAndFlush(
        JpaRepository<E, UUID> repository,
        EntityManager entityManager,
        E entity,
        Long versionBefore,
        Function<E, Long> version,
        String label
    ) {
        try {
            E saved = repository.saveAndFlush(entity);
            if (versionBefore != null && Objects.equals(version.apply(saved), versionBefore)) {
                entityManager.lock(saved, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            }
            return saved;
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw new OptimisticLockConflictException(label);
        }
    }
}
