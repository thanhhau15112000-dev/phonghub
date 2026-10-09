package com.phonghub.adapter.out.storage;

import com.phonghub.application.port.out.FileStoragePort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFileStorageAdapter implements FileStoragePort {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final Map<String, SignedToken> tokens = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryFileStorageAdapter() {
        this(Clock.systemUTC());
    }

    public InMemoryFileStorageAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        objects.put(objectKey, new StoredObject(contentType, Arrays.copyOf(content, content.length)));
    }

    @Override
    public byte[] get(String objectKey) {
        StoredObject stored = objects.get(objectKey);
        if (stored == null) {
            throw new IllegalArgumentException("Stored object not found: " + objectKey);
        }
        return Arrays.copyOf(stored.content(), stored.content().length);
    }

    @Override
    public URI createSignedUrl(String objectKey, Duration ttl) {
        if (!objects.containsKey(objectKey)) {
            throw new IllegalArgumentException("Stored object not found: " + objectKey);
        }
        String token = UUID.randomUUID().toString();
        tokens.put(token, new SignedToken(objectKey, clock.instant().plus(ttl)));
        return URI.create("memory://private-files/" + token);
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
        tokens.entrySet().removeIf(entry -> entry.getValue().objectKey().equals(objectKey));
    }

    public Optional<byte[]> openSignedUrl(URI signedUrl) {
        String token = signedUrl.getPath().substring(1);
        SignedToken signedToken = tokens.get(token);
        if (signedToken == null || !clock.instant().isBefore(signedToken.expiresAt())) {
            return Optional.empty();
        }
        StoredObject storedObject = objects.get(signedToken.objectKey());
        return storedObject == null
            ? Optional.empty()
            : Optional.of(Arrays.copyOf(storedObject.content(), storedObject.content().length));
    }

    private record StoredObject(String contentType, byte[] content) {}

    private record SignedToken(String objectKey, Instant expiresAt) {}
}
