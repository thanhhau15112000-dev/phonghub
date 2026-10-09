package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Chạy trên Tomcat thật để kiểm tra giới hạn multipart mà MockMvc không áp dụng.
 * Hạ giới hạn xuống 1MB để file vượt mức vẫn nhỏ hơn ngưỡng Tomcat tự ngắt kết nối (2MB).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.servlet.multipart.max-file-size=1MB",
        "spring.servlet.multipart.max-request-size=1MB"
    }
)
@ActiveProfiles("test")
class FileUploadRealServerTest {

    private static final String BOUNDARY = "----phonghubboundary";

    @Value("${local.server.port}")
    private int port;

    private int upload(byte[] content, String contentType) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"category\"\r\n\r\ndocuments\r\n")
            .getBytes(StandardCharsets.UTF_8));
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f.pdf\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/properties/" + DataSeeder.PROP_1_ID + "/files"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static byte[] pdf(int totalBytes) {
        byte[] content = new byte[totalBytes];
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, content, 0, header.length);
        return content;
    }

    @Test
    @DisplayName("A valid small PDF is accepted on the real server")
    void smallPdfIsAccepted() throws Exception {
        assertEquals(201, upload(pdf(2048), "application/pdf"));
    }

    @Test
    @DisplayName("A file above the configured limit returns 400 on the real server, not 413")
    void oversizedFileReturns400() throws Exception {
        assertEquals(400, upload(pdf(1536 * 1024), "application/pdf"));
    }
}
