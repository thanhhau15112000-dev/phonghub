package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Một người trong bộ chọn "Ai là người thuê?". Hồ sơ ({@code tenants}) và tài khoản ({@code users}) của cùng một người
 * được gộp thành một dòng để người dùng không phải hiểu hai bảng dữ liệu; chỉ tài khoản chưa có hồ sơ mới là dòng riêng.
 */
public record TenantChoice(
    UUID profileId,
    UUID userId,
    String fullName,
    String maskedIdentityCard,
    String phone,
    String email,
    String address,
    boolean hasAccount,
    boolean busy
) {

    /**
     * @param profiles       hồ sơ người thuê có thể chọn
     * @param busyProfileIds hồ sơ đang có hợp đồng mở (không cho chọn)
     * @param accounts       tài khoản TENANT khả dụng; tài khoản đã gắn với một hồ sơ trong {@code profiles} chỉ hiện một lần
     */
    public static List<TenantChoice> merge(List<Tenant> profiles, Set<UUID> busyProfileIds, List<User> accounts) {
        Set<UUID> linkedUserIds = new HashSet<>();
        List<TenantChoice> choices = new ArrayList<>();
        for (Tenant profile : profiles) {
            if (profile.userId() != null) {
                linkedUserIds.add(profile.userId());
            }
            choices.add(new TenantChoice(profile.id(), profile.userId(), profile.fullName(),
                profile.maskedIdentityCardNumber(), profile.phone(), profile.email(), profile.permanentAddress(),
                profile.userId() != null, busyProfileIds.contains(profile.id())));
        }
        Map<UUID, User> accountsById = accounts.stream()
            .collect(Collectors.toMap(User::id, Function.identity(), (first, ignored) -> first));
        for (User account : accountsById.values()) {
            if (!linkedUserIds.contains(account.id())) {
                choices.add(new TenantChoice(null, account.id(), account.fullName(), "", account.phone(),
                    account.email(), null, true, false));
            }
        }
        choices.sort(Comparator.comparing(TenantChoice::fullName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(choices);
    }
}
