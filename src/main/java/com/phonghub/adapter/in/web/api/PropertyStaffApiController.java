package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.PropertyStaffUseCase;
import com.phonghub.application.port.in.PropertyStaffUseCase.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties/{propertyId}/staff")
public class PropertyStaffApiController {

    private final PropertyStaffUseCase useCase;

    public PropertyStaffApiController(PropertyStaffUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<MemberResponse> list(@PathVariable UUID propertyId) {
        return useCase.listMembers(propertyId).stream().map(MemberResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<MemberResponse> assign(
        @PathVariable UUID propertyId,
        @Valid @RequestBody AssignRequest request
    ) {
        Member member = useCase.assign(new PropertyStaffUseCase.AssignCommand(propertyId, request.usernameOrEmail(),
            on(request.canCollectPayment()), on(request.canManageContracts()), on(request.canViewDocuments())));
        return ResponseEntity.created(URI.create("/api/properties/" + propertyId + "/staff/" + member.user().id()))
            .body(MemberResponse.from(member));
    }

    @PutMapping("/{userId}")
    public MemberResponse updatePermissions(
        @PathVariable UUID propertyId,
        @PathVariable UUID userId,
        @RequestBody PermissionsRequest request
    ) {
        return MemberResponse.from(useCase.updatePermissions(propertyId, userId, on(request.canCollectPayment()),
            on(request.canManageContracts()), on(request.canViewDocuments())));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unassign(@PathVariable UUID propertyId, @PathVariable UUID userId) {
        useCase.unassign(propertyId, userId);
        return ResponseEntity.noContent().build();
    }

    public record AssignRequest(
        @NotBlank(message = "Cần nhập tên đăng nhập hoặc email") String usernameOrEmail,
        Boolean canCollectPayment,
        Boolean canManageContracts,
        Boolean canViewDocuments
    ) {}

    /** Trường vắng mặt hiểu là false (không cấp quyền). */
    public record PermissionsRequest(Boolean canCollectPayment, Boolean canManageContracts, Boolean canViewDocuments) {}

    private static boolean on(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    public record MemberResponse(UUID userId, String username, String fullName, String role,
                                 boolean canCollectPayment, boolean canManageContracts, boolean canViewDocuments,
                                 Instant assignedAt) {
        static MemberResponse from(Member member) {
            return new MemberResponse(member.user().id(), member.user().username(), member.user().fullName(),
                member.user().role().name(), member.assignment().canCollectPayment(),
                member.assignment().canManageContracts(), member.assignment().canViewDocuments(),
                member.assignment().assignedAt());
        }
    }
}
