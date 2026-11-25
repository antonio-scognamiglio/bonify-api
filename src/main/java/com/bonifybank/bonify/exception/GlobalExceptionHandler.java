package com.bonifybank.bonify.exception;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

import com.bonifybank.bonify.exception.dto.ErrorResponse;

// Global error handling: intercepts application-layer exceptions in one place (@ControllerAdvice).
// 401 (unauthenticated) comes from the security filter entry point; 403 from @PreAuthorize
// method security is thrown inside the DispatcherServlet, so it is resolved here, and the
// filter-chain accessDeniedHandler covers 403 raised by the security filters themselves.
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    private static final Map<ErrorCodes, HttpStatus> STATUS_BY_CODE;
    private static final Map<ErrorCodes, String> TITLE_BY_CODE;

    static {
        STATUS_BY_CODE = new EnumMap<>(ErrorCodes.class);
        STATUS_BY_CODE.put(ErrorCodes.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
        STATUS_BY_CODE.put(ErrorCodes.SAME_ACCOUNT, HttpStatus.BAD_REQUEST);
        STATUS_BY_CODE.put(ErrorCodes.WRONG_CURRENT_PASSWORD, HttpStatus.BAD_REQUEST);
        STATUS_BY_CODE.put(ErrorCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        STATUS_BY_CODE.put(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN);
        STATUS_BY_CODE.put(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND);
        STATUS_BY_CODE.put(ErrorCodes.EMAIL_ALREADY_USED, HttpStatus.CONFLICT);
        STATUS_BY_CODE.put(ErrorCodes.TAX_CODE_ALREADY_USED, HttpStatus.CONFLICT);
        STATUS_BY_CODE.put(ErrorCodes.IDEMPOTENCY_KEY_REUSED, HttpStatus.CONFLICT);
        STATUS_BY_CODE.put(ErrorCodes.INSUFFICIENT_FUNDS,    HttpStatus.UNPROCESSABLE_CONTENT);
        STATUS_BY_CODE.put(ErrorCodes.ACCOUNT_BLOCKED,        HttpStatus.UNPROCESSABLE_CONTENT);
        STATUS_BY_CODE.put(ErrorCodes.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);

        TITLE_BY_CODE = new EnumMap<>(ErrorCodes.class);
        TITLE_BY_CODE.put(ErrorCodes.VALIDATION_ERROR, "Invalid input");
        TITLE_BY_CODE.put(ErrorCodes.SAME_ACCOUNT, "Source and destination are the same account");
        TITLE_BY_CODE.put(ErrorCodes.WRONG_CURRENT_PASSWORD, "Wrong current password");
        TITLE_BY_CODE.put(ErrorCodes.UNAUTHORIZED, "Unauthorized");
        TITLE_BY_CODE.put(ErrorCodes.FORBIDDEN, "Forbidden");
        TITLE_BY_CODE.put(ErrorCodes.NOT_FOUND, "Not found");
        TITLE_BY_CODE.put(ErrorCodes.EMAIL_ALREADY_USED, "Email already in use");
        TITLE_BY_CODE.put(ErrorCodes.TAX_CODE_ALREADY_USED, "Tax code already in use");
        TITLE_BY_CODE.put(ErrorCodes.IDEMPOTENCY_KEY_REUSED, "Idempotency key reused with a different body");
        TITLE_BY_CODE.put(ErrorCodes.INSUFFICIENT_FUNDS, "Insufficient funds");
        TITLE_BY_CODE.put(ErrorCodes.ACCOUNT_BLOCKED, "Account blocked");
        TITLE_BY_CODE.put(ErrorCodes.INTERNAL_ERROR, "Internal error");
    }

    private static String typeFor(ErrorCodes code) {
        return "https://bonify.example/errors/" + code.name().toLowerCase();
    }

    private static String titleFor(ErrorCodes code) {
        return TITLE_BY_CODE.getOrDefault(code, "Internal error");
    }

    private static HttpStatus statusFor(ErrorCodes code) {
        return STATUS_BY_CODE.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ErrorResponse problemDetail(ErrorCodes code, String detail, String instance) {
        return new ErrorResponse(
                typeFor(code),
                titleFor(code),
                statusFor(code).value(),
                detail,
                instance,
                code.name(),
                LocalDateTime.now());
    }

    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // All responses are RFC 7807 problem details with application/problem+json
    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorResponse error) {
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(error);
    }

    // Application-layer business errors → status per code
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return respond(statusFor(ex.getCode()), problemDetail(ex.getCode(), ex.getMessage(), pathOf(request)));
    }

    // Validation failed → 400 VALIDATION_ERROR, detail = field errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fieldErrors.put(e.getField(), e.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, problemDetail(ErrorCodes.VALIDATION_ERROR, "Field errors: " + fieldErrors, pathOf(request)));
    }

    // Unsupported API version → 400 VALIDATION_ERROR
    @ExceptionHandler(InvalidApiVersionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidApiVersion(InvalidApiVersionException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, problemDetail(ErrorCodes.VALIDATION_ERROR, ex.getMessage(), pathOf(request)));
    }

    // Unknown path → 404 NOT_FOUND
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, problemDetail(ErrorCodes.NOT_FOUND, "Not found", pathOf(request)));
    }

    // IllegalArgumentException → 400 (safety net)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, problemDetail(ErrorCodes.VALIDATION_ERROR, ex.getMessage(), pathOf(request)));
    }

    // @PreAuthorize role/ownership check failed → 403 FORBIDDEN
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, problemDetail(ErrorCodes.FORBIDDEN, ex.getMessage(), pathOf(request)));
    }

    // Fallback: any other exception → 500 INTERNAL_ERROR, no stack trace
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, problemDetail(ErrorCodes.INTERNAL_ERROR, "Internal server error", pathOf(request)));
    }
}
