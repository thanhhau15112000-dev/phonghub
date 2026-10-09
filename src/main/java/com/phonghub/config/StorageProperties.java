package com.phonghub.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "phonghub.storage")
public class StorageProperties {

    @NotBlank
    private String bucket = "private-files";

    @Min(1)
    private long maxFileSizeBytes = 10 * 1024 * 1024;

    @Min(1)
    @Max(600)
    private long signedUrlTtlSeconds = 600;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public long getSignedUrlTtlSeconds() {
        return signedUrlTtlSeconds;
    }

    public void setSignedUrlTtlSeconds(long signedUrlTtlSeconds) {
        this.signedUrlTtlSeconds = signedUrlTtlSeconds;
    }
}
