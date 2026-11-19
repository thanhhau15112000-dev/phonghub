package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterSubmissionRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MeterSubmission;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

class AdminFlowFixesIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;
    @Autowired private InMemoryInvoiceRepository invoiceRepository;
    @Autowired private InMemoryNotificationRepository notificationRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryMeterSubmissionRepository meterSubmissionRepository;

    @AfterEach
    void clearNotifications() {
        notificationRepository.clear();
        meterSubmissionRepository.clear();
    }

    @Test
    @DisplayName("Contract detail: ADMIN sees Giấy tờ for co-occupant, not Sửa or Rời phòng; OWNER still sees them")
    void contractDetailHidesOccupantEditAndCheckoutFromAdmin() throws Exception {
        Tenant coTenant = tenantRepository.save(Tenant.create(
            null, "Người Ở Cùng", "079200007777", "0907777666", null, null));
        var contract = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contract.addOccupant(coTenant.id(), false, LocalDate.now());
        contractRepository.save(contract);

        String adminHtml = mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(adminHtml.contains("/tenants/" + coTenant.id() + "/documents"));
        assertFalse(adminHtml.contains("edit-occupant-btn"));
        assertFalse(adminHtml.contains("checkout-occupant-btn"));

        String ownerHtml = mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(ownerHtml.contains("edit-occupant-btn"));
        assertTrue(ownerHtml.contains("checkout-occupant-btn"));
    }

    @Test
    @DisplayName("Maintenance detail: ADMIN does not see Thanh toán or Xem hóa đơn tháng links")
    void maintenanceDetailHidesInvoicePayLinksFromAdmin() throws Exception {
        MaintenanceTicket payableTicket = ticketRepository.save(new MaintenanceTicket(
            UUID.randomUUID(), DataSeeder.ROOM_101_ID, DataSeeder.PROP_1_ID, DataSeeder.TENANT_RECORD_ID,
            LocalDemoAuthenticationAdapter.TECH_1_ID, "Hỏng ổ cắm", "Cần thay mới",
            MaintenancePriority.MEDIUM, MaintenanceStatus.AWAITING_PAYMENT, new BigDecimal("150000"),
            "Đã thay", MaintenanceCause.TENANT_USAGE, LiableParty.TENANT, Instant.now(), Instant.now()));

        Invoice fee = invoiceRepository.save(new Invoice(
            UUID.randomUUID(), DataSeeder.CONTRACT_1_ID, DataSeeder.ROOM_101_ID, DataSeeder.TENANT_RECORD_ID,
            LocalDate.now().getMonthValue(), LocalDate.now().getYear(), new BigDecimal("150000"),
            new BigDecimal("150000"), BigDecimal.ZERO, LocalDate.now().plusDays(3), InvoiceStatus.ISSUED,
            "PHFEE123", InvoiceType.MAINTENANCE, payableTicket.getId(), null, Instant.now(), Instant.now()));
        String payUrl = "/invoices/" + fee.getId() + "/pay";

        mockMvc.perform(get("/maintenance/" + payableTicket.getId()).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(payUrl))))
            .andExpect(content().string(containsString("Miễn phí")));

        mockMvc.perform(get("/maintenance/" + payableTicket.getId()).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(payUrl)));

        UUID monthlyId = UUID.randomUUID();
        fee.consolidateInto(monthlyId);
        invoiceRepository.save(fee);
        String consolidatedUrl = "/invoices/" + monthlyId + "/pay";

        mockMvc.perform(get("/maintenance/" + payableTicket.getId()).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(consolidatedUrl))));

        mockMvc.perform(get("/maintenance/" + payableTicket.getId()).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Khoản này đã được gộp vào hóa đơn tháng.")))
            .andExpect(content().string(containsString(consolidatedUrl)));
    }

    @Test
    @DisplayName("Admin user-edit page includes layout footer for header menus and confirmation dialog")
    void adminUserEditIncludesFooter() throws Exception {
        mockMvc.perform(get("/admin/users/" + OWNER_2 + "/edit").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("class=\"site-footer\"")))
            .andExpect(content().string(containsString("id=\"appConfirmDialog\"")));
    }

    @Test
    @DisplayName("Admin users pagination hides single page, omits jump on <=3 pages, and renders page 2")
    void adminUsersPaginationFixes() throws Exception {
        // Seeded data has 7 users -> with size=10, totalPages=1 -> pagination omitted
        mockMvc.perform(get("/admin/users").param("size", "10").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalPages", 1))
            .andExpect(content().string(not(containsString("class=\"pagination-container\""))));

        // With size=5, totalPages=2 -> pagination shown, no jump input
        mockMvc.perform(get("/admin/users").param("size", "5").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalPages", 2))
            .andExpect(content().string(containsString("class=\"pagination-container\"")))
            .andExpect(content().string(not(containsString("class=\"page-jump-form\""))));

        // Add users so size=5 -> 16 users => 4 pages (totalPages=4)
        for (int i = 0; i < 9; i++) {
            userRepository.save(new User(UUID.randomUUID(), "extra" + i, "extra" + i + "@phonghub.local",
                "Extra User " + i, null, UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()));
        }

        // Page 2 of 4: page 2 is active and rendered, jump form is rendered
        String page2Of4 = mockMvc.perform(get("/admin/users").param("page", "2").param("size", "5").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalPages", 4))
            .andExpect(model().attribute("currentPage", 2))
            .andReturn().getResponse().getContentAsString();
        assertTrue(page2Of4.contains("<span class=\"page-link current\">2</span>"));
        assertTrue(page2Of4.contains("class=\"page-jump-form\""));

        // With size=6, 16 users => 3 pages (totalPages=3), page 2 rendered, no jump input
        String page2Of3 = mockMvc.perform(get("/admin/users").param("page", "2").param("size", "6").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalPages", 3))
            .andExpect(model().attribute("currentPage", 2))
            .andReturn().getResponse().getContentAsString();
        assertTrue(page2Of3.contains("<span class=\"page-link current\">2</span>"));
        assertFalse(page2Of3.contains("class=\"page-jump-form\""));
    }

    @Test
    @DisplayName("Admin users UI includes search button, data-confirm on reset password, alerts, and URL-encoded redirects")
    void adminUsersUiAndRedirectEncoding() throws Exception {
        String html = mockMvc.perform(get("/admin/users").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains(">Tìm kiếm</button>"));
        assertTrue(html.contains("data-confirm-title=\"Reset mật khẩu\""));

        // Out-of-range page with Vietnamese keyword URL-encodes safely
        mockMvc.perform(get("/admin/users").param("q", "Nguyễn Văn").param("page", "99").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users?q=Nguy%E1%BB%85n%20V%C4%83n"));

        // Out-of-range page on /admin/notifications also URL-encodes keyword safely
        mockMvc.perform(get("/admin/notifications").param("q", "Nguyễn Văn").param("page", "99").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/notifications?q=nguy%E1%BB%85n%20v%C4%83n"));
    }

    @Test
    @DisplayName("attachTenantProfile puts general errors in profileErrorMessage (card 1) instead of tableErrorMessage")
    void attachTenantProfileUsesProfileErrorMessage() throws Exception {
        // Seeded user TENANT_1 already has a tenant profile, so attaching again triggers DomainException -> generalError
        mockMvc.perform(post("/admin/users/" + LocalDemoAuthenticationAdapter.TENANT_1_ID + "/tenant-profile")
                .header("X-User-Id", ADMIN)
                .param("phone", "0912345678")
                .param("identityCardNumber", "079201009999"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users"))
            .andExpect(model().attributeExists("profileErrorMessage"))
            .andExpect(model().attributeDoesNotExist("tableErrorMessage"));
    }

    @Test
    @DisplayName("Admin notification search filters badge counts and header dropdown prioritizes unresolved items")
    void adminNotificationSearchCountsAndHeaderDropdownPriority() throws Exception {
        User tenant = userRepository.findById(LocalDemoAuthenticationAdapter.TENANT_1_ID).orElseThrow();
        User owner = userRepository.findById(LocalDemoAuthenticationAdapter.OWNER_1_ID).orElseThrow();

        // Older unresolved notification
        Notification unresolved = Notification.createPasswordResetRequest(tenant);
        ReflectionTestUtils.setField(unresolved, "createdAt", Instant.now().minusSeconds(3600));
        notificationRepository.save(unresolved);

        // 5 newer resolved notifications
        for (int i = 0; i < 5; i++) {
            Notification resolved = Notification.createPasswordResetRequest(owner);
            resolved.markResolved("Đã xong " + i);
            ReflectionTestUtils.setField(resolved, "createdAt", Instant.now().minusSeconds(60L * (i + 1)));
            notificationRepository.save(resolved);
        }

        // Header dropdown (on /dashboard) must include unresolved item in top 5 despite 5 newer resolved ones
        String dashboardHtml = mockMvc.perform(get("/dashboard").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(dashboardHtml.contains("tenant1"), "Unresolved notification for tenant1 must appear in top 5 header dropdown");

        // Search on /admin/notifications filters badge counts to matched items
        mockMvc.perform(get("/admin/notifications").param("q", "tenant1").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalCount", 1L))
            .andExpect(model().attribute("unresolvedCount", 1L))
            .andExpect(model().attribute("resolvedCount", 0L));
    }

    @Test
    @DisplayName("Admin property creation via UI requires selecting an owner and links ownerId; Owner sees + Thêm nhà trọ on dashboard")
    void adminCreatePropertyRequiresOwnerOnUi() throws Exception {
        String listHtml = mockMvc.perform(get("/properties").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(listHtml.contains("id=\"ownerId\""));
        assertTrue(listHtml.contains(OWNER_1));

        mockMvc.perform(post("/properties")
                .header("X-User-Id", ADMIN)
                .param("name", "Nhà trọ Thiếu Chủ")
                .param("address", "123 Lê Lợi")
                .param("totalRooms", "5"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/properties"))
            .andExpect(flash().attribute("errorMessage", "Vui lòng chọn chủ trọ sở hữu cho nhà trọ."));

        mockMvc.perform(post("/properties")
                .header("X-User-Id", ADMIN)
                .param("name", "Nhà trọ Có Chủ")
                .param("address", "456 Hai Bà Trưng")
                .param("totalRooms", "8")
                .param("ownerId", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        Property created = propertyRepository.findAll().stream()
            .filter(p -> p.name().equals("Nhà trọ Có Chủ"))
            .findFirst().orElseThrow();
        assertEquals(LocalDemoAuthenticationAdapter.OWNER_1_ID, created.ownerId());
        assertEquals(PropertyApprovalStatus.VERIFIED, created.approvalStatus());

        String ownerDashboard = mockMvc.perform(get("/dashboard").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(ownerDashboard.contains("+ Thêm nhà trọ"));
    }

    @Test
    @DisplayName("Admin scope standardization: no late-fee, room-creation, blind basePrice edit, or meter approval controls")
    void adminScopeStandardizationOnPropertyAndRoom() throws Exception {
        String adminPropHtml = mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(adminPropHtml.contains("/late-fee"));
        assertFalse(adminPropHtml.contains("Thêm phòng kèm biên bản hiện trạng"));
        assertFalse(adminPropHtml.contains("Tạo nhiều phòng"));
        assertTrue(adminPropHtml.contains("/staff"));

        String ownerPropHtml = mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(ownerPropHtml.contains("/late-fee"));
        assertTrue(ownerPropHtml.contains("Thêm phòng kèm biên bản hiện trạng"));

        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID + "/late-fee").header("X-User-Id", ADMIN))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));

        // Room edit modal hides basePrice for ADMIN and server ignores basePrice submitted by ADMIN on UI endpoint
        String adminRoomHtml = mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_104_ID).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(adminRoomHtml.contains("id=\"editRoomModal\""));
        assertFalse(adminRoomHtml.contains("id=\"editBasePrice\""));

        Room before = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        mockMvc.perform(post("/rooms/" + DataSeeder.ROOM_104_ID + "/edit")
                .header("X-User-Id", ADMIN)
                .param("roomNumber", "P104")
                .param("floor", "1")
                .param("areaSqm", "28")
                .param("basePrice", "1000")
                .param("maxOccupants", "2")
                .param("version", Long.toString(before.getVersion())))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));
        Room after = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        assertEquals(0, before.getBasePrice().compareTo(after.getBasePrice()));

        // Meter submissions: ADMIN can view, does not see Duyệt / Từ chối forms
        meterSubmissionRepository.save(new MeterSubmission(
            UUID.randomUUID(), DataSeeder.ROOM_101_ID, MeterType.ELECTRIC, new BigDecimal("1234"),
            "meter-photo-key", "image/png", new BigDecimal("1234"), new BigDecimal("0.99"), "tesseract",
            MeterSubmission.MatchStatus.MATCH, MeterSubmission.Status.PENDING, LocalDemoAuthenticationAdapter.TENANT_1_ID,
            Instant.now(), null, null, null, null, null));
        String adminMeterHtml = mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID + "/meter-submissions")
                .header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(adminMeterHtml.contains("Chờ duyệt"));
        assertFalse(adminMeterHtml.contains("/accept"));
        assertFalse(adminMeterHtml.contains("/reject"));
    }
}
