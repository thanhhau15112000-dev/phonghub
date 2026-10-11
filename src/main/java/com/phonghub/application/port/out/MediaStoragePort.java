package com.phonghub.application.port.out;

public interface MediaStoragePort {
    /**
     * Store media file (image/video) and return the accessible URL.
     * @param originalFilename original file name
     * @param contentType MIME type (e.g. image/jpeg, video/mp4)
     * @param bytes binary data
     * @return accessible URL (CDN URL or local endpoint)
     */
    String store(String originalFilename, String contentType, byte[] bytes);
}
