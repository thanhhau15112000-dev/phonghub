package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tiền thuê, tiền cọc của hợp đồng chỉ hiện với chủ trọ, nhân viên có quyền thu tiền hoặc quản lý hợp đồng và người
 * thuê của hợp đồng (REQ-CON-04, #304). Quản trị viên, kỹ thuật viên và nhân viên không có quyền vẫn xem được hợp đồng
 * nhưng không thấy số tiền, ở API, trang hợp đồng và trang phòng. Quản trị viên không còn lập hay quản lý hợp đồng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractMoneyVisibilityIntegrationTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    /** Tiền thuê riêng, khác giá niêm yết của phòng (3.500.000) để không nhầm với giá phòng trên trang. */
    private static final String RENT_RAW = "3456000";
    private static final String RENT_SHOWN = "3.456.000 đ";
    private static final String DEPOSIT_RAW = "3500000";
    private static final String DEPOSIT_SHOWN = "3.500.000 đ";

    private static final String API_CONTRACT = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String API_LIST = "/api/properties/" + DataSeeder.PROP_1_ID + "/contracts";
    private static final String CONTRACT_PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String ROOM_PAGE = "/rooms/" + DataSeeder.ROOM_101_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;

    @BeforeEach
    void resetData() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
        // Staff 2 được giao nhà 1 nhưng không có quyền thu tiền hay quản lý hợp đồng
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), LocalDemoAuthenticationAdapter.STAFF_2_ID,
            DataSeeder.PROP_1_ID, false, false, Instant.now()));
        Contract contract = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contract.renew(contract.getEndDate().plusMonths(1), new BigDecimal(RENT_RAW), LocalDate.now());
        contractRepository.save(contract);
    }

    @Test
    @DisplayName("API: chủ trọ, nhân viên có quyền, người thuê nhận tiền thuê và tiền cọc")
    void apiReturnsMoneyToAllowedRoles() throws Exception {
        for (String user : new String[] {OWNER_1, STAFF_1, TENANT_1}) {
            mockMvc.perform(get(API_CONTRACT).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(RENT_RAW)))
                .andExpect(content().string(containsString(DEPOSIT_RAW)));
        }
        for (String user : new String[] {OWNER_1, STAFF_1}) {
            mockMvc.perform(get(API_LIST).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(RENT_RAW)));
        }
    }

    @Test
    @DisplayName("API: quản trị viên, kỹ thuật viên, nhân viên không có quyền xem được hợp đồng nhưng không có tiền")
    void apiHidesMoneyFromRestrictedRoles() throws Exception {
        for (String user : new String[] {ADMIN, TECH_1, STAFF_2}) {
            mockMvc.perform(get(API_CONTRACT).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(DataSeeder.CONTRACT_1_ID.toString())))
                .andExpect(content().string(not(containsString(RENT_RAW))))
                .andExpect(content().string(not(containsString(DEPOSIT_RAW))));
            mockMvc.perform(get(API_LIST).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(DataSeeder.CONTRACT_1_ID.toString())))
                .andExpect(content().string(not(containsString(RENT_RAW))))
                .andExpect(content().string(not(containsString(DEPOSIT_RAW))));
        }
    }

    @Test
    @DisplayName("Giao diện: chủ trọ và người thuê thấy tiền trên trang hợp đồng, danh sách và trang phòng")
    void pagesShowMoneyToAllowedRoles() throws Exception {
        for (String user : new String[] {OWNER_1, STAFF_1, TENANT_1}) {
            mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(RENT_SHOWN)))
                .andExpect(content().string(containsString(DEPOSIT_SHOWN)));
            mockMvc.perform(get("/contracts").header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(RENT_SHOWN)));
        }
        mockMvc.perform(get(ROOM_PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(RENT_SHOWN)));
    }

    @Test
    @DisplayName("Giao diện: quản trị viên, kỹ thuật viên, nhân viên không có quyền không thấy tiền hợp đồng")
    void pagesHideMoneyFromRestrictedRoles() throws Exception {
        for (String user : new String[] {ADMIN, TECH_1, STAFF_2}) {
            mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(RENT_SHOWN))))
                .andExpect(content().string(not(containsString(DEPOSIT_SHOWN))))
                .andExpect(content().string(not(containsString(RENT_RAW))))
                .andExpect(content().string(not(containsString("id=\"editDraftModal\""))))
                .andExpect(content().string(not(containsString("id=\"renewModal\""))));
            mockMvc.perform(get("/contracts").header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(RENT_SHOWN))))
                .andExpect(content().string(not(containsString(DEPOSIT_SHOWN))));
            mockMvc.perform(get(ROOM_PAGE).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(RENT_SHOWN))));
        }
    }

    @Test
    @DisplayName("Quản trị viên không lập hợp đồng: API 403, form tạo chuyển về danh sách, không có nút tạo")
    void adminCannotCreateContracts() throws Exception {
        String body = """
            {"propertyId":"%s","roomId":"%s","primaryTenantId":"%s","depositAmount":4000000,"rentAmount":4000000,
             "startDate":"%s","endDate":"%s","paymentDay":5}
            """.formatted(DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, DataSeeder.TENANT_RECORD_ID,
            LocalDate.now(), LocalDate.now().plusYears(1));
        mockMvc.perform(post("/api/contracts").header("X-User-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden())
            .andExpect(content().string(containsString(AuthorizationService.ADMIN_CONTRACT_DENIED)));
        mockMvc.perform(get("/contracts/new").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/contracts"));
        mockMvc.perform(get("/contracts").header("X-User-Id", ADMIN))
            .andExpect(content().string(not(containsString("href=\"/contracts/new\""))))
            .andExpect(content().string(not(containsString("id=\"export-link\""))));
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/contracts/new?propertyId="))));
    }

    @Test
    @DisplayName("Quản trị viên không vào được trang xuất dữ liệu")
    void adminCannotOpenExports() throws Exception {
        mockMvc.perform(get("/exports").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"))
            .andExpect(flash().attribute("errorMessage", AuthorizationService.ADMIN_FINANCE_DENIED));
        mockMvc.perform(get("/exports/preview").param("dataset", "contracts").header("X-User-Id", ADMIN))
            .andExpect(status().isForbidden());
    }
}
