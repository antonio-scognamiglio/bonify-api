package com.bonifybank.bonify.exception;

public class BusinessException extends RuntimeException {
    private final ErrorCodes code;

    public BusinessException(ErrorCodes code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCodes getCode() {
        return code;
    }
}
