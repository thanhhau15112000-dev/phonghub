package com.phonghub.config;

import com.phonghub.adapter.out.storage.InMemoryFileStorageAdapter;
import com.phonghub.adapter.out.storage.SupabaseFileStorageAdapter;
import com.phonghub.application.port.out.FileStoragePort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @Profile("prod")
    public FileStoragePort supabaseFileStoragePort(
        RestClient restClient,
        SupabaseProperties supabaseProperties,
        StorageProperties storageProperties
    ) {
        return new SupabaseFileStorageAdapter(
            restClient,
            supabaseProperties.getUrl(),
            supabaseProperties.getServiceRoleKey(),
            storageProperties.getBucket()
        );
    }

    @Bean
    @Profile({"test", "docker & !prod"})
    public FileStoragePort inMemoryFileStoragePort() {
        org.slf4j.LoggerFactory.getLogger(StorageConfig.class).warn(
            "Kho tệp đang lưu trong bộ nhớ: ảnh/video sẽ mất khi khởi động lại ứng dụng. Chỉ dùng cho máy cá nhân hoặc test.");
        return new InMemoryFileStorageAdapter();
    }
}
