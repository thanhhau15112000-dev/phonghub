package com.phonghub.adapter.out.storage;

import com.phonghub.application.port.out.FileStoragePort;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class SupabaseFileStorageAdapter implements FileStoragePort {

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String bucket;

    public SupabaseFileStorageAdapter(
        RestClient restClient,
        String supabaseUrl,
        String serviceRoleKey,
        String bucket
    ) {
        this.restClient = restClient;
        this.supabaseUrl = stripTrailingSlash(supabaseUrl);
        this.serviceRoleKey = serviceRoleKey;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        restClient.post()
            .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectKey)
            .header("apikey", serviceRoleKey)
            .header("Authorization", "Bearer " + serviceRoleKey)
            .header("x-upsert", "false")
            .contentType(MediaType.parseMediaType(contentType))
            .body(content)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public URI createSignedUrl(String objectKey, Duration ttl) {
        SignedUrlResponse response = restClient.post()
            .uri(supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + objectKey)
            .header("apikey", serviceRoleKey)
            .header("Authorization", "Bearer " + serviceRoleKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("expiresIn", ttl.toSeconds()))
            .retrieve()
            .body(SignedUrlResponse.class);
        if (response == null || response.signedURL() == null || response.signedURL().isBlank()) {
            throw new IllegalStateException("Supabase Storage did not return a signed URL");
        }
        String signedUrl = response.signedURL();
        return URI.create(signedUrl.startsWith("http") ? signedUrl : supabaseUrl + "/storage/v1" + signedUrl);
    }

    @Override
    public void delete(String objectKey) {
        restClient.delete()
            .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectKey)
            .header("apikey", serviceRoleKey)
            .header("Authorization", "Bearer " + serviceRoleKey)
            .retrieve()
            .toBodilessEntity();
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record SignedUrlResponse(String signedURL) {}
}
