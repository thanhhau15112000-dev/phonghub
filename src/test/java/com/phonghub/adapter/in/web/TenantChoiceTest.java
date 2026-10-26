package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bộ chọn "Ai là người thuê?" gộp hồ sơ và tài khoản của cùng một người thành một dòng (#198). */
class TenantChoiceTest {

    private static User account(String name, String email) {
        return new User(UUID.randomUUID(), email, name, "0900000000", UserRole.TENANT, User.UserStatus.ACTIVE, Instant.now());
    }

    @Test
    @DisplayName("A profile and its linked account become one row; an account without a profile is its own row")
    void mergesLinkedAccountIntoItsProfile() {
        User linked = account("Trần Thị C", "c@example.com");
        User orphan = account("Bùi Văn B", "b@example.com");
        Tenant withAccount = Tenant.create(linked.id(), "Trần Thị C", "079200003333", "0923456789", "c@example.com", null);
        Tenant withoutAccount = Tenant.create(null, "Lê Văn A", "079200001111", "0901234567", null, null);

        List<TenantChoice> choices = TenantChoice.merge(List.of(withAccount, withoutAccount), Set.of(),
            List.of(linked, orphan));

        assertEquals(3, choices.size(), "hai hồ sơ + một tài khoản chưa có hồ sơ, tài khoản đã gắn không hiện lần hai");
        assertEquals(List.of("Bùi Văn B", "Lê Văn A", "Trần Thị C"), choices.stream().map(TenantChoice::fullName).toList());
        TenantChoice orphanRow = choices.get(0);
        assertNull(orphanRow.profileId());
        assertEquals(orphan.id(), orphanRow.userId());
        assertTrue(orphanRow.hasAccount());
        assertEquals("", orphanRow.maskedIdentityCard());
        assertTrue(choices.get(1).profileId() != null && !choices.get(1).hasAccount());
        assertTrue(choices.get(2).hasAccount());
    }

    @Test
    @DisplayName("A profile with an open contract is marked busy whichever way it is reached, and the full CCCD never leaves the server")
    void marksBusyProfilesAndMasksIdentity() {
        Tenant busy = Tenant.create(null, "Nguyễn Văn A", "079201001111", "0901234567", null, null);
        Tenant free = Tenant.create(null, "Phạm Thị D", "079200008888", "0933444666", null, null);

        List<TenantChoice> choices = TenantChoice.merge(List.of(busy, free), Set.of(busy.id()), List.of());

        TenantChoice busyRow = choices.stream().filter(c -> c.profileId().equals(busy.id())).findFirst().orElseThrow();
        TenantChoice freeRow = choices.stream().filter(c -> c.profileId().equals(free.id())).findFirst().orElseThrow();
        assertTrue(busyRow.busy());
        assertFalse(freeRow.busy());
        assertEquals("****1111", busyRow.maskedIdentityCard());
        assertFalse(choices.toString().contains("079201001111"));
    }
}
