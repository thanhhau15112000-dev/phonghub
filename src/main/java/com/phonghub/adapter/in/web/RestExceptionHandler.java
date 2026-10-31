package com.phonghub.adapter.in.web;

import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.NoExportDataException;
import com.phonghub.domain.exception.NotificationNotFoundException;
import com.phonghub.domain.exception.DocumentRevealDeniedException;
import com.phonghub.domain.exception.DuplicateAssetCodeException;
import com.phonghub.domain.exception.DuplicateServiceRateException;
import com.phonghub.domain.exception.MeterReadingConflictException;
import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.domain.exception.UtilityNotFoundException;
import com.phonghub.domain.exception.HandoverAlreadyExistsException;
import com.phonghub.domain.exception.HandoverNotFoundException;
import com.phonghub.domain.exception.InvalidContractStateException;
import com.phonghub.domain.exception.InvalidHandoverStateException;
import com.phonghub.domain.exception.InvalidMaintenanceStateException;
import com.phonghub.domain.exception.DuplicateDocumentNumberException;
import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.DuplicateIdentityCardException;
import com.phonghub.domain.exception.DuplicateRoomNumberException;
import com.phonghub.domain.exception.DuplicateUsernameException;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.exception.InvalidRoomCapacityException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.exception.PasswordChangeRequiredException;
import com.phonghub.domain.exception.PrimaryOccupantRemovalException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.InvoiceNotFoundException;
import com.phonghub.domain.exception.ReceiptNotAvailableException;
import com.phonghub.domain.exception.RoomAssetNotFoundException;
import com.phonghub.domain.exception.TenantDocumentNotFoundException;
import com.phonghub.domain.exception.TooManyRevealAttemptsException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.AttachmentLimitException;
import com.phonghub.domain.exception.StaffAssignmentConflictException;
import com.phonghub.domain.exception.TenantProfileLockedException;
import com.phonghub.domain.exception.TenantHasActiveContractException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.exception.AccountDisabledException;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.PasswordChangeRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.phonghub.adapter.in.web.api")
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("https://phonghub.local/errors/unauthorized"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IdentityProviderUnavailableException.class)
    public ProblemDetail handleIdentityProviderUnavailable(IdentityProviderUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Identity Provider Unavailable");
        problem.setType(URI.create("https://phonghub.local/errors/identity-provider-unavailable"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(com.phonghub.domain.exception.FileStorageUnavailableException.class)
    public ProblemDetail handleFileStorageUnavailable(com.phonghub.domain.exception.FileStorageUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("File Storage Unavailable");
        problem.setType(URI.create("https://phonghub.local/errors/file-storage-unavailable"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ProblemDetail handleAccountDisabled(AccountDisabledException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Account Disabled");
        problem.setType(URI.create("https://phonghub.local/errors/account-disabled"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ProblemDetail handlePasswordChangeRequired(PasswordChangeRequiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Password Change Required");
        problem.setType(URI.create("https://phonghub.local/errors/password-change-required"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(UnauthorizedPropertyAccessException.class)
    public ProblemDetail handleUnauthorizedAccess(UnauthorizedPropertyAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Unauthorized Property Access");
        problem.setType(URI.create("https://phonghub.local/errors/forbidden"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(com.phonghub.domain.exception.UnauthorizedChatAccessException.class)
    public ProblemDetail handleUnauthorizedChatAccess(com.phonghub.domain.exception.UnauthorizedChatAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Unauthorized Chat Access");
        problem.setType(URI.create("https://phonghub.local/errors/forbidden"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(NoExportDataException.class)
    public ProblemDetail handleNoExportData(NoExportDataException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("No Export Data");
        problem.setType(URI.create("https://phonghub.local/errors/no-export-data"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DuplicateActiveContractException.class)
    public ProblemDetail handleDuplicateActiveContract(DuplicateActiveContractException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Active Contract");
        problem.setType(URI.create("https://phonghub.local/errors/duplicate-active-contract"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidPropertyStatusException.class)
    public ProblemDetail handleInvalidPropertyStatus(InvalidPropertyStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invalid Property Status");
        problem.setType(URI.create("https://phonghub.local/errors/invalid-property-status"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler({
        DuplicateEmailException.class,
        DuplicateUsernameException.class,
        DuplicateAssetCodeException.class,
        DuplicateDocumentNumberException.class,
        InvalidHandoverStateException.class,
        ReceiptNotAvailableException.class,
        InvalidMaintenanceStateException.class,
        InvalidContractStateException.class,
        HandoverAlreadyExistsException.class,
        MeterReadingConflictException.class,
        DuplicateServiceRateException.class,
        TenantHasActiveContractException.class,
        StaffAssignmentConflictException.class,
        TenantProfileLockedException.class,
        AttachmentLimitException.class,
        OptimisticLockConflictException.class,
        com.phonghub.domain.exception.ConflictException.class
    })
    public ProblemDetail handleDuplicateAccountConflict(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://phonghub.local/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidRoomStateException.class)
    public ProblemDetail handleInvalidRoomState(InvalidRoomStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invalid Room State Transition");
        problem.setType(URI.create("https://phonghub.local/errors/invalid-room-state"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DuplicateRoomNumberException.class)
    public ProblemDetail handleDuplicateRoomNumber(DuplicateRoomNumberException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Room Number");
        problem.setType(URI.create("https://phonghub.local/errors/duplicate-room-number"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidRoomCapacityException.class)
    public ProblemDetail handleInvalidRoomCapacity(InvalidRoomCapacityException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invalid Room Capacity");
        problem.setType(URI.create("https://phonghub.local/errors/invalid-room-capacity"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DuplicateIdentityCardException.class)
    public ProblemDetail handleDuplicateIdentityCard(DuplicateIdentityCardException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Identity Card");
        problem.setType(URI.create("https://phonghub.local/errors/duplicate-identity-card"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(PrimaryOccupantRemovalException.class)
    public ProblemDetail handlePrimaryOccupantRemoval(PrimaryOccupantRemovalException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Primary Occupant Removal Not Allowed");
        problem.setType(URI.create("https://phonghub.local/errors/primary-occupant-removal"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(com.phonghub.domain.exception.InvalidChatTargetException.class)
    public ProblemDetail handleInvalidChatTarget(com.phonghub.domain.exception.InvalidChatTargetException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://phonghub.local/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler({
        PropertyNotFoundException.class,
        InvoiceNotFoundException.class,
        RoomNotFoundException.class,
        RoomAssetNotFoundException.class,
        TenantDocumentNotFoundException.class,
        HandoverNotFoundException.class,
        UtilityNotFoundException.class,
        ContractNotFoundException.class,
        UserNotFoundException.class,
        TenantNotFoundException.class,
        NotificationNotFoundException.class,
        com.phonghub.domain.exception.ChatRoomNotFoundException.class,
        com.phonghub.domain.exception.ChatMessageNotFoundException.class
    })
    public ProblemDetail handleNotFound(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://phonghub.local/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MaintenanceTicketException.class)
    public ProblemDetail handleMaintenanceTicketError(MaintenanceTicketException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Maintenance Ticket Error");
        problem.setType(URI.create("https://phonghub.local/errors/maintenance-ticket-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Domain Invariant Violation");
        problem.setType(URI.create("https://phonghub.local/errors/domain-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://phonghub.local/errors/validation-error"));
        problem.setProperty("timestamp", Instant.now());

        StringBuilder sb = new StringBuilder();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", sb.toString());
        return problem;
    }

    @ExceptionHandler(DocumentRevealDeniedException.class)
    public ProblemDetail handleRevealDenied(DocumentRevealDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Reveal Denied");
        problem.setType(URI.create("https://phonghub.local/errors/reveal-denied"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(TooManyRevealAttemptsException.class)
    public ProblemDetail handleTooManyReveals(TooManyRevealAttemptsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Too Many Requests");
        problem.setType(URI.create("https://phonghub.local/errors/too-many-requests"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Vi phạm ràng buộc duy nhất ở tầng cơ sở dữ liệu (ví dụ hai yêu cầu cùng lúc) được trả về là xung đột. */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(org.springframework.dao.DuplicateKeyException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Dữ liệu đã tồn tại hoặc vừa được cập nhật bởi một yêu cầu khác");
        problem.setTitle("Conflict");
        problem.setType(URI.create("https://phonghub.local/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Argument");
        problem.setType(URI.create("https://phonghub.local/errors/invalid-argument"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Lỗi ngoài dự kiến (#164): trả ProblemDetail 500 cùng định dạng với các lỗi khác, không lộ thông điệp hay stack
     * trace; chi tiết chỉ ghi log. Lỗi chuẩn của Spring MVC (400 sai kiểu/thiếu tham số, 405, 415...) được ném lại để
     * Spring trả đúng mã như trước.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof ErrorResponse || ex instanceof TypeMismatchException
            || ex instanceof HttpMessageNotReadableException || ex instanceof HttpMessageNotWritableException) {
            throw ex;
        }
        log.error("Lỗi không xử lý ở API {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Hệ thống gặp lỗi, vui lòng thử lại sau.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://phonghub.local/errors/internal"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
