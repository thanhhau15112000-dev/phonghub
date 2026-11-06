package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.domain.model.Contract;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kiểm thử hộp thoại xác nhận chuẩn UI thay thế confirm() trình duyệt (#224).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActionConfirmDialogUiIntegrationTest extends SeededWebTest {

    private static final String ADMIN_ID = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryContractRepository contractRepository;

    @Test
    @DisplayName("Tất cả template HTML không còn chứa lệnh confirm() mặc định của trình duyệt")
    void templatesDoNotContainNativeConfirm() throws IOException {
        Path templatesDir = Paths.get("src", "main", "resources", "templates");
        assertTrue(Files.exists(templatesDir), "Thư mục templates phải tồn tại");

        Pattern nativeConfirmPattern = Pattern.compile("(?<!on)confirm\\s*\\(");

        try (Stream<Path> paths = Files.walk(templatesDir)) {
            List<String> filesWithNativeConfirm = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".html"))
                .filter(p -> {
                    try {
                        String content = Files.readString(p);
                        return nativeConfirmPattern.matcher(content).find();
                    } catch (IOException e) {
                        return false;
                    }
                })
                .map(Path::toString)
                .collect(Collectors.toList());

            assertTrue(filesWithNativeConfirm.isEmpty(),
                "Không được phép dùng confirm() trình duyệt trong templates: " + filesWithNativeConfirm);
        }
    }

    @Test
    @DisplayName("Hộp thoại xác nhận appConfirmDialog được nhúng qua layout footer")
    void layoutContainsAppConfirmDialog() throws Exception {
        mockMvc.perform(get("/contracts").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"appConfirmDialog\"")))
            .andExpect(content().string(containsString("class=\"modal-dialog\"")))
            .andExpect(content().string(containsString("id=\"appConfirmTitle\"")))
            .andExpect(content().string(containsString("id=\"appConfirmMessage\"")))
            .andExpect(content().string(containsString("id=\"appConfirmTargetWrapper\"")))
            .andExpect(content().string(containsString("id=\"appConfirmConsequenceWrapper\"")))
            .andExpect(content().string(containsString("id=\"appConfirmPhraseGroup\"")))
            .andExpect(content().string(containsString("id=\"appConfirmReasonGroup\"")))
            .andExpect(content().string(containsString("id=\"appConfirmSubmitBtn\"")))
            .andExpect(content().string(containsString("openConfirmModal")));
    }

    @Test
    @DisplayName("Chi tiết hợp đồng: Hủy hóa đơn yêu cầu lý do và cụm từ HỦY HÓA ĐƠN (#224)")
    void contractDetailVoidInvoiceConfirmation() throws Exception {
        String period = java.time.YearMonth.from(java.time.LocalDate.now().minusMonths(1)).toString();
        mockMvc.perform(post("/contracts/" + DataSeeder.CONTRACT_1_ID + "/invoices")
                .header("X-User-Id", OWNER_1)
                .param("period", period))
            .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Kiểm tra nút hủy hóa đơn có đầy đủ thuộc tính data-confirm, phrase và lý do
        assertTrue(html.contains("data-confirm-phrase=\"HỦY HÓA ĐƠN\""),
            "Nút hủy hóa đơn phải yêu cầu cụm từ HỦY HÓA ĐƠN");
        assertTrue(html.contains("data-confirm-require-reason"),
            "Nút hủy hóa đơn phải yêu cầu nhập lý do");
        assertTrue(html.contains("data-confirm-title=\"Hủy hóa đơn tiền thuê\""),
            "Tiêu đề hộp thoại phải là Hủy hóa đơn tiền thuê");
    }

    @Test
    @DisplayName("Chi tiết phòng: Xóa tài sản và ngừng sử dụng phòng dùng hộp thoại xác nhận")
    void roomDetailActionConfirmations() throws Exception {
        mockMvc.perform(post("/rooms/" + DataSeeder.ROOM_101_ID + "/assets")
                .header("X-User-Id", OWNER_1)
                .param("name", "Tủ lạnh")
                .param("quantity", "1")
                .param("estimatedValue", "4000000"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-confirm-title=\"Xóa tài sản\"")))
            .andExpect(content().string(containsString("data-confirm-title=\"Ngừng sử dụng phòng\"")))
            .andExpect(content().string(not(containsString("confirm("))));
    }

    @Test
    @DisplayName("Nhà trọ và nhân sự: Duyệt nhà trọ, ẩn nhà trọ và gỡ nhân sự dùng hộp thoại xác nhận")
    void propertyAndStaffConfirmations() throws Exception {
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID).header("X-User-Id", ADMIN_ID))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-confirm-title=\"Ẩn nhà trọ\"")))
            .andExpect(content().string(not(containsString("confirm("))));

        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID + "/staff").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-confirm-title=\"Gỡ nhân sự\"")))
            .andExpect(content().string(not(containsString("confirm("))));
    }

    @Test
    @DisplayName("Issue #213: Hủy hợp đồng nháp yêu cầu cụm xác nhận HỦY HỢP ĐỒNG")
    void contractDetailDraftCancelRequiresPhrase() throws Exception {
        Contract draft = Contract.create(
            DataSeeder.PROP_1_ID,
            DataSeeder.ROOM_104_ID,
            DataSeeder.TENANT_RECORD_ID,
            new BigDecimal("1000000"),
            new BigDecimal("4000000"),
            LocalDate.of(2026, 12, 1),
            LocalDate.of(2027, 12, 1),
            5
        );
        contractRepository.save(draft);

        // Access detail of created draft contract
        String detailHtml = mockMvc.perform(get("/contracts/" + draft.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(detailHtml.contains("data-confirm-phrase=\"HỦY HỢP ĐỒNG\""),
            "Nút hủy hợp đồng nháp phải có data-confirm-phrase=\"HỦY HỢP ĐỒNG\"");
        assertTrue(detailHtml.contains("data-confirm-title=\"Hủy hợp đồng nháp\""),
            "Tiêu đề hộp thoại phải là Hủy hợp đồng nháp");
    }

    @Test
    @DisplayName("Issue #213: Chi tiết hợp đồng: Gia hạn và kết thúc sớm yêu cầu cụm xác nhận riêng")
    void contractDetailRenewAndTerminateModalsRequirePhrases() throws Exception {
        String html = mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Renew modal
        assertTrue(html.contains("id=\"renewModal\""), "Trang phải có modal gia hạn renewModal");
        assertTrue(html.contains("id=\"renewConfirmPhraseInput\""), "Modal gia hạn phải có ô nhập cụm từ xác nhận");
        assertTrue(html.contains("GIA HẠN HỢP ĐỒNG"), "Modal gia hạn phải hiển thị yêu cầu cụm từ GIA HẠN HỢP ĐỒNG");
        assertTrue(html.contains("id=\"renewSubmitBtn\""), "Modal gia hạn phải có nút submit riêng");

        // Terminate modal
        assertTrue(html.contains("id=\"terminateModal\""), "Trang phải có modal terminateModal");
        assertTrue(html.contains("id=\"terminateConfirmPhraseInput\""), "Modal kết thúc sớm phải có ô nhập cụm từ xác nhận");
        assertTrue(html.contains("CHẤM DỨT HỢP ĐỒNG"), "Modal kết thúc sớm phải hiển thị yêu cầu cụm từ CHẤM DỨT HỢP ĐỒNG");
        assertTrue(html.contains("id=\"terminateSubmitBtn\""), "Modal kết thúc sớm phải có nút submit riêng");
    }

    @Test
    @DisplayName("Issue #213: Quản trị người dùng: Xóa tài khoản vĩnh viễn yêu cầu cụm xác nhận XÓA TÀI KHOẢN")
    void adminUserDeleteModalRequiresPhrase() throws Exception {
        String html = mockMvc.perform(get("/admin/users").header("X-User-Id", ADMIN_ID))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("id=\"deleteConfirmModal\""), "Trang phải có modal deleteConfirmModal");
        assertTrue(html.contains("id=\"deleteModalPhraseInput\""), "Modal phải có ô nhập cụm từ deleteModalPhraseInput");
        assertTrue(html.contains("XÓA TÀI KHOẢN"), "Modal phải yêu cầu nhập cụm từ XÓA TÀI KHOẢN");
        assertTrue(html.contains("id=\"deleteModalSubmitBtn\""), "Nút xóa phải có id deleteModalSubmitBtn");
        assertTrue(html.contains("disabled"), "Nút xóa phải mặc định bị disabled khi chưa nhập đúng");
    }
}

