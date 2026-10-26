package com.phonghub.application.port.out;

import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import java.util.Optional;
import java.util.UUID;

public interface HandoverRepositoryPort {

    Handover save(Handover handover);

    void delete(UUID handoverId);

    Optional<Handover> find(UUID contractId, HandoverType type);

    /** Khóa dòng biên bản trong transaction để hai lần duyệt cùng lúc không cùng thành công. */
    default Optional<Handover> findForUpdate(UUID contractId, HandoverType type) {
        return find(contractId, type);
    }
}
