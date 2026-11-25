package com.bonifybank.bonify.exception;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
    void entityNotFoundExceptionIsNotFound() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBusiness(new EntityNotFoundException("Customer", 12), request);

        ErrorResponse body = response.getBody();
        assert response.getStatusCode().value() == HttpStatus.NOT_FOUND.value();
        assert body.code() == ErrorCodes.NOT_FOUND.name();
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
