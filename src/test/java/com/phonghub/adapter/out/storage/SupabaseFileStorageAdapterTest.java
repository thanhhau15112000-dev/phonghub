package com.phonghub.adapter.out.storage;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseFileStorageAdapterTest {

    private static final String BASE = "https://project.supabase.co";
    private static final String KEY = "service-role-key";

    private MockRestServiceServer server;
    private SupabaseFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SupabaseFileStorageAdapter(builder.build(), BASE + "/", KEY, "private-files");
    }

    @Test
    void putUploadsToPrivateBucketWithServiceKeyAndWithoutOverwrite() {
        byte[] payload = {1, 2, 3};
        server.expect(requestTo(BASE + "/storage/v1/object/private-files/properties/p/documents/a.pdf"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer " + KEY))
            .andExpect(header("apikey", KEY))
            .andExpect(header("x-upsert", "false"))
            .andExpect(header("Content-Type", MediaType.APPLICATION_PDF_VALUE))
            .andExpect(content().bytes(payload))
            .andRespond(withSuccess());

        adapter.put("properties/p/documents/a.pdf", "application/pdf", payload);

        server.verify();
    }

    @Test
    void getDownloadsObjectBytes() {
        byte[] payload = {9, 8, 7};
        server.expect(requestTo(BASE + "/storage/v1/object/private-files/properties/p/a.bin"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + KEY))
            .andRespond(withSuccess(payload, MediaType.APPLICATION_OCTET_STREAM));

        assertArrayEquals(payload, adapter.get("properties/p/a.bin"));
        server.verify();
    }

    @Test
    void signedUrlRequestsRequestedLifetimeAndBuildsAbsoluteUrl() {
        server.expect(requestTo(BASE + "/storage/v1/object/sign/private-files/properties/p/a.png"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.expiresIn").value(300))
            .andRespond(withSuccess("{\"signedURL\":\"/object/sign/private-files/a.png?token=abc\"}",
                MediaType.APPLICATION_JSON));

        URI url = adapter.createSignedUrl("properties/p/a.png", Duration.ofMinutes(5));

        assertEquals(URI.create(BASE + "/storage/v1/object/sign/private-files/a.png?token=abc"), url);
    }

    @Test
    void signedUrlWithoutPayloadFails() {
        server.expect(requestTo(BASE + "/storage/v1/object/sign/private-files/x.png"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> adapter.createSignedUrl("x.png", Duration.ofMinutes(1)));
    }

    @Test
    void deleteCallsStorageDelete() {
        server.expect(requestTo(BASE + "/storage/v1/object/private-files/a.png"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess());

        adapter.delete("a.png");

        server.verify();
    }

    @Test
    void storageErrorsPropagate() {
        server.expect(requestTo(BASE + "/storage/v1/object/private-files/a.png"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        assertThrows(org.springframework.web.client.RestClientResponseException.class,
            () -> adapter.put("a.png", "image/png", new byte[]{1}));
    }
}
