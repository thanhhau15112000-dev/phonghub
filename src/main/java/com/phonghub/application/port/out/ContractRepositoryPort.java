package com.phonghub.application.port.out;

import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepositoryPort {
    Contract save(Contract contract);
    Optional<Contract> findById(UUID id);
    List<Contract> findByPropertyId(UUID propertyId);
    List<Contract> findByRoomId(UUID roomId);

    /**
     * Hợp đồng của người đang ở phòng: ACTIVE, hoặc EXPIRED nhưng chưa check-out (#87). Mỗi phòng có tối đa một hợp
     * đồng như vậy vì không tạo/kích hoạt được hợp đồng mới khi phòng còn người ở.
     */
    Optional<Contract> findActiveByRoomId(UUID roomId);
    List<Contract> findByPrimaryTenantId(UUID tenantId);
    List<Contract> findByOccupantTenantId(UUID tenantId);
    List<Contract> findAll();
}
