package com.phonghub.config;

import com.phonghub.application.port.in.RoomSetupUseCase;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import com.phonghub.domain.model.Room;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalRoomSetupUseCase implements RoomSetupUseCase {
    private final RoomSetupUseCase delegate;
    public TransactionalRoomSetupUseCase(RoomSetupUseCase delegate) { this.delegate = delegate; }

    @Override @Transactional(rollbackFor = Exception.class)
    public RoomSetupResult createRoom(CreateRoomCommand command) { return delegate.createRoom(command); }

    @Override @Transactional(rollbackFor = Exception.class)
    public List<RoomSetupResult> createRooms(BulkCreateCommand command) { return delegate.createRooms(command); }

    @Override @Transactional(readOnly = true)
    public RoomConditionDetails latestCondition(UUID roomId) { return delegate.latestCondition(roomId); }
}
