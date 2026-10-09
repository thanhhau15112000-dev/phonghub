package com.phonghub.application.port.out;

import com.phonghub.domain.model.Handover;
import java.util.Optional;
import java.util.UUID;

public interface HandoverRepositoryPort {

    Handover save(Handover handover);

    Optional<Handover> findByContractId(UUID contractId);

    /** Khóa dòng biên bản trong transaction để hai lần duyệt cùng lúc không cùng thành công. */
    default Optional<Handover> findByContractIdForUpdate(UUID contractId) {
        return findByContractId(contractId);
    }
}
