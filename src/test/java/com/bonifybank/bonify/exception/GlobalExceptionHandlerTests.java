package com.bonifybank.bonify.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.mock.web.MockHttpServletRequest;

import com.bonifybank.bonify.exception.dto.ErrorResponse;

/**
 * Unit tests: every BusinessException code maps to its RFC 7807 status + code,
 * and the validation / illegal-argument / fallback handlers produce the expected records.
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    {
        request.setRequestURI("/api/v1/customers");
    }

    //              code,                        status
    static Stream<Arguments> codes() {
        return Stream.of(
                Arguments.of(ErrorCodes.VALIDATION_ERROR,       HttpStatus.BAD_REQUEST),
                Arguments.of(ErrorCodes.SAME_ACCOUNT,           HttpStatus.BAD_REQUEST),
                Arguments.of(ErrorCodes.WRONG_CURRENT_PASSWORD, HttpStatus.BAD_REQUEST),
                Arguments.of(ErrorCodes.UNAUTHORIZED,           HttpStatus.UNAUTHORIZED),
                Arguments.of(ErrorCodes.FORBIDDEN,             HttpStatus.FORBIDDEN),
                Arguments.of(ErrorCodes.NOT_FOUND,             HttpStatus.NOT_FOUND),
                Arguments.of(ErrorCodes.METHOD_NOT_ALLOWED,    HttpStatus.METHOD_NOT_ALLOWED),
                Arguments.of(ErrorCodes.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                Arguments.of(ErrorCodes.EMAIL_ALREADY_USED,    HttpStatus.CONFLICT),
                Arguments.of(ErrorCodes.IDEMPOTENCY_KEY_REUSED, HttpStatus.CONFLICT),
                Arguments.of(ErrorCodes.INSUFFICIENT_FUNDS,    HttpStatus.UNPROCESSABLE_CONTENT),
                Arguments.of(ErrorCodes.ACCOUNT_BLOCKED,        HttpStatus.UNPROCESSABLE_CONTENT),
                Arguments.of(ErrorCodes.INTERNAL_ERROR,        HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    void businessExceptionMapsToStatusAndCode(ErrorCodes code, HttpStatus expected) {
        ResponseEntity<ErrorResponse> response =
                handler.handleBusiness(new BusinessException(code, code.name() + " message"), request);

        ErrorResponse body = response.getBody();
        assert body != null;
        assert response.getStatusCode().value() == expected.value();
        assert body.status() == expected.value();
        assert body.code() == code.name();
        assert body.title() != null;
        assert body.type() != null;
        assert body.instance() != null;
        assert body.detail() != null;
        assert body.timestamp() != null;
    }

    @Test
    void methodArgumentNotValidIsValidationError() throws Exception {
        MethodParameter param = new MethodParameter(Thrower.class.getMethod("throwIt", Object.class), 0);
        BeanPropertyBindingResult binder = new BeanPropertyBindingResult(new Object(), "body");
        binder.addError(new FieldError("body", "email", "email is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, binder);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);
        ErrorResponse body = response.getBody();

        assert response.getStatusCode().value() == HttpStatus.BAD_REQUEST.value();
        assert body.code() == ErrorCodes.VALIDATION_ERROR.name();
        assert body.detail().contains("email");
        assert body.detail().contains("email is required");
    }

    @Test
    void illegalArgumentIsValidationError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("bad arg"), request);

        ErrorResponse body = response.getBody();
        assert response.getStatusCode().value() == HttpStatus.BAD_REQUEST.value();
        assert body.code() == ErrorCodes.VALIDATION_ERROR.name();
    }

    @Test
    void missingHeaderIsValidationError() throws Exception {
        MethodParameter param = new MethodParameter(Thrower.class.getMethod("throwIt", Object.class), 0);

        ResponseEntity<ErrorResponse> response =
                handler.handleMissingHeader(new MissingRequestHeaderException("Idempotency-Key", param), request);

        assertValidationError(response, "Missing required header: Idempotency-Key");
    }

    @Test
    void missingParameterIsValidationError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMissingParameter(new MissingServletRequestParameterException("size", "int"), request);

        assertValidationError(response, "Missing required parameter: size");
    }

    @Test
    void unreadableBodyIsValidationErrorWithoutParserDetails() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected end-of-input in com.example.Internal",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response = handler.handleNotReadable(ex, request);

        assertValidationError(response, "Request body is missing or malformed");
    }

    @Test
    void typeMismatchIsValidationError() throws Exception {
        MethodParameter param = new MethodParameter(Thrower.class.getMethod("throwIt", Object.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "id", param, new IllegalArgumentException("bad"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertValidationError(response, "Invalid value for 'id'");
    }

    @Test
    void unsupportedMethodIsMethodNotAllowedWithAllowHeader() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.METHOD_NOT_ALLOWED.name());
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void unsupportedMediaTypeIs415() {
        ResponseEntity<ErrorResponse> response = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException("Content-Type 'text/plain' is not supported"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.UNSUPPORTED_MEDIA_TYPE.name());
    }

    @Test
    void problemTypeUsesDashesForMultiWordCodes() {
        ErrorResponse body = handler.handleBusiness(
                new BusinessException(ErrorCodes.WRONG_CURRENT_PASSWORD, "wrong"), request).getBody();

        assertThat(body.type()).isEqualTo("https://bonify.example/errors/wrong-current-password");
        assertThat(body.instance()).isEqualTo("/api/v1/customers");
    }

    @Test
    void accessDeniedIsForbidden() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("Access Denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.FORBIDDEN.name());
        assertThat(response.getBody().status()).isEqualTo(403);
    }

    @Test
    void unknownResourceIsNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/nothing", "nothing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.NOT_FOUND.name());
    }

    @Test
    void invalidApiVersionIsValidationError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidApiVersion(new InvalidApiVersionException("9"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.VALIDATION_ERROR.name());
    }

    private static void assertValidationError(ResponseEntity<ErrorResponse> response, String detail) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.VALIDATION_ERROR.name());
        assertThat(response.getBody().detail()).isEqualTo(detail);
    }

    @Test
    void genericExceptionIsInternalError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("boom"), request);

        ErrorResponse body = response.getBody();
        assert response.getStatusCode().value() == HttpStatus.INTERNAL_SERVER_ERROR.value();
        assert body.code() == ErrorCodes.INTERNAL_ERROR.name();
        // No stack trace leaks into the body
        assert !body.detail().contains("boom");
    }

    // Holder for a single-method parameter so MethodParameter.forMethodOrConstructor works.
    public static class Thrower {
        public void throwIt(Object body) {
        }
    }
}
