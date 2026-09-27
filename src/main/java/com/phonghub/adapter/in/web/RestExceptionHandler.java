package com.phonghub.adapter.in.web;

import com.phonghub.domain.exception.ContractNotFoundException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateActiveContractException;
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
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.exception.UserNotFoundException;
import com.phonghub.domain.exception.AccountDisabledException;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.PasswordChangeRequiredException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.phonghub.adapter.in.web.api")
public class RestExceptionHandler {

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
        DuplicateUsernameException.class
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

    @ExceptionHandler({
        PropertyNotFoundException.class,
        RoomNotFoundException.class,
        ContractNotFoundException.class,
        UserNotFoundException.class,
        TenantNotFoundException.class
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Argument");
        problem.setType(URI.create("https://phonghub.local/errors/invalid-argument"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
