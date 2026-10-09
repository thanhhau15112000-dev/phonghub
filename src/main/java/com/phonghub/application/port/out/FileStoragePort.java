package com.phonghub.application.port.out;

import java.net.URI;
import java.time.Duration;

public interface FileStoragePort {

    void put(String objectKey, String contentType, byte[] content);

    byte[] get(String objectKey);

    URI createSignedUrl(String objectKey, Duration ttl);

    void delete(String objectKey);
}
