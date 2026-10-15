package com.phonghub.adapter.out.supabase.storage;

import com.phonghub.adapter.out.storage.LocalMediaStorageAdapter;
import com.phonghub.application.port.out.MediaStoragePort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupabaseMediaStorageAdapter implements MediaStoragePort {

    private static final Logger log = LoggerFactory.getLogger(SupabaseMediaStorageAdapter.class);

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String bucketName;
    private final LocalMediaStorageAdapter localFallback;
    private final HttpClient httpClient;

    public SupabaseMediaStorageAdapter(String supabaseUrl, String serviceRoleKey, String bucketName) {
        this.supabaseUrl = supabaseUrl != null ? supabaseUrl.replaceAll("/+$", "") : "";
        this.serviceRoleKey = serviceRoleKey != null ? serviceRoleKey.trim() : "";
        this.bucketName = (bucketName != null && !bucketName.isBlank()) ? bucketName.trim() : "chat-media";
        this.localFallback = new LocalMediaStorageAdapter();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean isConfigured() {
        return !supabaseUrl.isBlank() && !serviceRoleKey.isBlank();
    }

    @Override
    public String store(String originalFilename, String contentType, byte[] bytes) {
        if (!isConfigured()) {
            log.info("Supabase Storage chưa được cấu hình credentials, sử dụng lưu trữ cục bộ.");
            return localFallback.store(originalFilename, contentType, bytes);
        }

        try {
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }
            String objectPath = UUID.randomUUID() + extension;
            String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath;

            String mediaContentType = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apiKey", serviceRoleKey)
                .header("Content-Type", mediaContentType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // CDN public URL từ Supabase Storage
                String cdnUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + objectPath;
                log.info("Upload media lên Supabase CDN thành công: {}", cdnUrl);
                return cdnUrl;
            } else {
                log.warn("Upload lên Supabase Storage thất bại (HTTP {}): {}. Chuyển sang lưu cục bộ fallback.",
                    response.statusCode(), response.body());
                return localFallback.store(originalFilename, contentType, bytes);
            }
        } catch (Exception e) {
            log.warn("Lỗi kết nối Supabase Storage: {}. Chuyển sang lưu cục bộ fallback.", e.getMessage());
            return localFallback.store(originalFilename, contentType, bytes);
        }
    }
}
