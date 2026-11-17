package com.phonghub.config;

import com.phonghub.application.port.in.PropertyStaffUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalPropertyStaffUseCase implements PropertyStaffUseCase {

    private final PropertyStaffUseCase delegate;

    public TransactionalPropertyStaffUseCase(PropertyStaffUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> listMembers(UUID propertyId) {
        return delegate.listMembers(propertyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> listCandidates(UUID propertyId, String query, int limit) {
        return delegate.listCandidates(propertyId, query, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Member assign(AssignCommand command) {
        return delegate.assign(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Member updatePermissions(UUID propertyId, UUID userId, boolean canCollectPayment, boolean canManageContracts,
                                    boolean canViewDocuments, boolean canManageRoomsAndAssets) {
        return delegate.updatePermissions(propertyId, userId, canCollectPayment, canManageContracts, canViewDocuments,
            canManageRoomsAndAssets);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unassign(UUID propertyId, UUID userId) {
        delegate.unassign(propertyId, userId);
    }
}
