package com.phonghub.adapter.out.storage;

import com.phonghub.application.port.out.MediaStoragePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalMediaStorageAdapter implements MediaStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalMediaStorageAdapter.class);
    private final Path uploadDir;

    public LocalMediaStorageAdapter() {
        this(Paths.get("uploads", "chat-media"));
    }

    public LocalMediaStorageAdapter(Path uploadDir) {
        this.uploadDir = uploadDir;
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            log.error("Không thể khởi tạo thư mục lưu trữ media: {}", uploadDir, e);
        }
    }

    @Override
    public String store(String originalFilename, String contentType, byte[] bytes) {
        try {
            Files.createDirectories(uploadDir);
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            } else if (contentType != null) {
                if (contentType.contains("jpeg") || contentType.contains("jpg")) extension = ".jpg";
                else if (contentType.contains("png")) extension = ".png";
                else if (contentType.contains("webp")) extension = ".webp";
                else if (contentType.contains("mp4")) extension = ".mp4";
                else if (contentType.contains("webm")) extension = ".webm";
            }

            String filename = UUID.randomUUID() + extension;
            Path target = uploadDir.resolve(filename);
            Files.write(target, bytes);

            log.info("Lưu media cục bộ thành công: {} ({} bytes)", target.toAbsolutePath(), bytes.length);
            return "/uploads/chat-media/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Lỗi lưu trữ tệp tin media cục bộ: " + e.getMessage(), e);
        }
    }
}
