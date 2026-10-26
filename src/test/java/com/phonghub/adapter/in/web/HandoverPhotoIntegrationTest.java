package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.domain.model.HandoverType;
import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.client.RestClientException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ảnh biên bản check-in/check-out (#200): tải lên, xem lại và báo lỗi kho lưu trữ. */
class HandoverPhotoIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String UI = "/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String API = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String BODY = "{\"initialElectricMeter\":1500,\"initialWaterMeter\":40,"
        + "\"roomCondition\":\"Phòng sạch\",\"keysProvided\":\"2 chìa khóa\"}";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final String STORAGE_ERROR = "Không lưu được ảnh vào kho lưu trữ";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryHandoverPhotoRepository photos;
    @Autowired private InMemoryHandoverItemRepository items;
    @Autowired private InMemoryTenantDocumentRepository documents;
    @MockitoSpyBean private FileStoragePort storage;

    @BeforeEach
    void reset() {
        handovers.clear();
        photos.clear();
        items.clear();
        documents.clear();
    }

    @Test
    @DisplayName("Ảnh check-in tải lên được và xem lại được khi kho lưu trữ là in-memory (profile docker/test)")
    void checkInPhotoCanBeViewedWithLocalStorage() throws Exception {
        createDraft("handover");
        String photoPath = uploadAndFindPhoto("handover");

        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));
        mockMvc.perform(get(photoPath).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Kho thật (Supabase) trả URL https: trang xem ảnh chuyển sang signed URL có thời hạn")
    void realStorageRedirectsToSignedUrl() throws Exception {
        createDraft("handover");
        String photoPath = uploadAndFindPhoto("handover");
        doReturn(URI.create("https://storage.example/object/sign/private-files/a.png?token=t"))
            .when(storage).createSignedUrl(anyString(), any());

        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_1))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://storage.example/object/sign/private-files/a.png?token=t"))
            .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("Người ngoài hợp đồng xem ảnh nhận 403, không bị chuyển sang trang khác")
    void outsiderGetsForbiddenOnPhoto() throws Exception {
        createDraft("handover");
        String photoPath = uploadAndFindPhoto("handover");

        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart(API + "/handover/photos").file(png()).param("kind", "ROOM")
                .header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Kho lưu trữ lỗi khi tải ảnh (giao diện): báo lỗi tiếng Việt, không tạo bản ghi ảnh")
    void storageFailureOnUploadShowsMessageInUi() throws Exception {
        createDraft("handover");
        doThrow(new RestClientException("404 Bucket not found")).when(storage).put(anyString(), anyString(), any());

        mockMvc.perform(multipart(UI + "/handover/photos").file(png()).param("kind", "ROOM")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", containsString(STORAGE_ERROR)));
        assertEquals(0, checkInPhotoCount(), "không có bản ghi ảnh mồ côi");
    }

    @Test
    @DisplayName("Kho lưu trữ lỗi khi tải ảnh (API): 503 kèm thông báo tiếng Việt, không tạo bản ghi ảnh")
    void storageFailureOnUploadReturns503FromApi() throws Exception {
        createDraft("handover");
        doThrow(new RestClientException("403 Unauthorized")).when(storage).put(anyString(), anyString(), any());

        mockMvc.perform(multipart(API + "/handover/photos").file(png()).param("kind", "ROOM")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail", containsString(STORAGE_ERROR)));
        assertEquals(0, checkInPhotoCount());
    }

    @Test
    @DisplayName("Kho lưu trữ lỗi khi xem ảnh: quay lại trang biên bản kèm thông báo thay vì trang lỗi 500")
    void storageFailureOnViewShowsMessage() throws Exception {
        createDraft("handover");
        String photoPath = uploadAndFindPhoto("handover");
        doThrow(new RestClientException("timeout")).when(storage).createSignedUrl(anyString(), any());

        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", containsString("Không mở được ảnh")));
    }

    @Test
    @DisplayName("Form ảnh mang giới hạn dung lượng để trình duyệt báo ngay khi chọn ảnh quá lớn")
    void uploadFormCarriesSizeLimit() throws Exception {
        createDraft("handover");

        mockMvc.perform(get(UI + "/handover").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-max-bytes=\"10485760\"")))
            .andExpect(content().string(containsString("upload-too-large")));
    }

    @Test
    @DisplayName("Ảnh sai định dạng: giao diện báo lỗi rõ, không tạo bản ghi ảnh")
    void invalidImageIsRejectedWithMessage() throws Exception {
        createDraft("handover");

        mockMvc.perform(multipart(UI + "/handover/photos")
                .file(new MockMultipartFile("file", "a.png", "image/png", "not an image".getBytes()))
                .param("kind", "ROOM").header("X-User-Id", OWNER_1))
            .andExpect(flash().attribute("errorMessage", containsString("không phải ảnh")));
        assertEquals(0, checkInPhotoCount());
    }

    @Test
    @DisplayName("Ảnh check-out tải lên được qua giao diện và API, xem lại được và hiện ở trang đối chiếu")
    void checkOutPhotoUploadAndView() throws Exception {
        approvedCheckIn();
        createDraft("check-out");
        String photoPath = uploadAndFindPhoto("check-out");

        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));
        mockMvc.perform(multipart(API + "/check-out/photos").file(png()).param("kind", "METER_WATER")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isCreated());
        mockMvc.perform(get(UI + "/handover-report").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(photoPath)));
    }

    private int checkInPhotoCount() {
        return handovers.find(DataSeeder.CONTRACT_1_ID, HandoverType.CHECK_IN)
            .map(handover -> photos.findByHandoverId(handover.id()).size())
            .orElseThrow();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "p.png", "image/png", PNG);
    }

    private void createDraft(String kind) throws Exception {
        mockMvc.perform(post(UI + "/" + kind).header("X-User-Id", OWNER_1).param("initialElectricMeter", "1500")
                .param("initialWaterMeter", "40").param("roomCondition", "Phòng sạch"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    /** Tải một ảnh phòng qua form rồi trả đường dẫn xem ảnh lấy từ trang biên bản. */
    private String uploadAndFindPhoto(String kind) throws Exception {
        mockMvc.perform(multipart(UI + "/" + kind + "/photos").file(png()).param("kind", "ROOM")
                .header("X-User-Id", OWNER_1))
            .andExpect(flash().attribute("successMessage", "Đã thêm ảnh."));
        String page = UI + "/" + kind;
        String html = mockMvc.perform(get(page).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString();
        String marker = page + "/photos/";
        int start = html.indexOf(marker);
        assertTrue(start >= 0, "trang biên bản có liên kết xem ảnh");
        return html.substring(start, start + marker.length() + 36);
    }

    /** Check-in đã duyệt, đủ điều kiện tạo check-out. */
    private void approvedCheckIn() throws Exception {
        mockMvc.perform(post(API + "/check-in").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        for (String kind : new String[]{"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(API + "/handover/photos").file(png()).param("kind", kind)
                .header("X-User-Id", OWNER_1)).andExpect(status().isCreated());
        }
        mockMvc.perform(post(API + "/handover/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform((MockMultipartHttpServletRequestBuilder) multipart("/api/properties/" + DataSeeder.PROP_1_ID
                + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/documents/CCCD").with(request -> {
                request.setMethod("PUT");
                return request;
            }).file(new MockMultipartFile("front", "f.png", "image/png", PNG)).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
        mockMvc.perform(post(API + "/handover/confirm").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
        mockMvc.perform(post(API + "/handover/approve").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
    }
}
