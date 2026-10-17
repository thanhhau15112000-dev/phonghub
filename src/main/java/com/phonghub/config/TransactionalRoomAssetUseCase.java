package com.phonghub.config;

import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomAssetEvent;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalRoomAssetUseCase implements RoomAssetUseCase {

    private final RoomAssetUseCase delegate;

    public TransactionalRoomAssetUseCase(RoomAssetUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomAsset create(CreateAssetCommand command) {
        return delegate.create(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RoomAsset> createFromTemplate(UUID roomId, String template) {
        return delegate.createFromTemplate(roomId, template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomAsset update(UpdateAssetCommand command) {
        return delegate.update(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID assetId) {
        delegate.delete(assetId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomAsset> list(UUID roomId) {
        return delegate.list(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomAsset get(UUID assetId) {
        return delegate.get(assetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomAssetEvent recordEvent(RecordEventCommand command) {
        return delegate.recordEvent(command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomAssetEvent> history(UUID assetId) {
        return delegate.history(assetId);
    }

    @Override
    @Transactional(readOnly = true)
    public URI imageSignedUrl(UUID assetId) {
        return delegate.imageSignedUrl(assetId);
    }
}
