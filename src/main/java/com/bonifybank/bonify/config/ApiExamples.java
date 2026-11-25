package com.bonifybank.bonify.config;

/**
 * JSON examples shown in Swagger UI (FR-PLT-01). They are compile-time constants so the controllers
 * can use them inside @ExampleObject. The values are consistent with each other (same IBANs, ids and
 * customer), so the examples read like one story: register, log in, open an account, move money.
 *
 * Error examples follow the RFC 7807 format produced by GlobalExceptionHandler and SecurityConfig.
 */
public final class ApiExamples {

    private ApiExamples() {
    }

    public static final String JSON = "application/json";
    public static final String PROBLEM_JSON = "application/problem+json";

    // Shared sample values
    public static final String IBAN = "IT60X0542811101000000123456";
    public static final String OTHER_IBAN = "IT60X0542811101000000654321";
    public static final String CUSTOMER_ID = "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01";
    public static final String TRANSFER_ID = "9b2e4f6a-1c3d-4e5f-8a7b-0c1d2e3f4a5b";
    public static final String IDEMPOTENCY_KEY = "5d0f6a2c-7e1b-4c3d-9f8e-1a2b3c4d5e6f";

    // ---------------------------------------------------------------- success bodies

    public static final String CUSTOMER_PROFILE = """
            {
              "id": "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01",
              "firstName": "Mario",
              "lastName": "Rossi",
              "email": "mario.rossi@example.com",
              "phoneNumber": "+39 333 1234567",
              "taxCode": "RSSMRA85T10A562S"
            }""";

    public static final String LOGIN_RESPONSE = """
            {
              "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzZjFjMmE3ZS04YjRkLTRjMWUtOWE1Zi0yZDZiN2U4YzlmMDEiLCJyb2xlIjoiQ1VTVE9NRVIifQ.signature",
              "tokenType": "Bearer",
              "expiresIn": 300,
              "refreshToken": "q8Xc1Zt4Rk2Lm9Np0Vb7Ws3Ed6Fg5Hj",
              "refreshExpiresIn": 604800
            }""";

    public static final String CUSTOMER = """
            {
              "id": "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01",
              "email": "mario.rossi@example.com",
              "firstName": "Mario",
              "lastName": "Rossi",
              "phoneNumber": "+39 333 1234567",
              "taxCode": "RSSMRA85T10A562S",
              "role": "CUSTOMER",
              "createdAt": "2026-11-25T09:00:00Z",
              "updatedAt": "2026-11-25T09:30:00Z"
            }""";

    public static final String CUSTOMER_PAGE = """
            {
              "content": [
                {
                  "id": "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01",
                  "email": "mario.rossi@example.com",
                  "firstName": "Mario",
                  "lastName": "Rossi",
                  "phoneNumber": "+39 333 1234567",
                  "taxCode": "RSSMRA85T10A562S",
                  "role": "CUSTOMER",
                  "createdAt": "2026-11-25T09:00:00Z",
                  "updatedAt": "2026-11-25T09:30:00Z"
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1,
              "last": true
            }""";

    public static final String NEW_ACCOUNT = """
            {
              "id": 1,
              "iban": "IT60X0542811101000000123456",
              "currency": "EUR",
              "balance": 0.00,
              "status": "ACTIVE",
              "customerId": "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01",
              "createdAt": "2026-11-25T10:00:00Z"
            }""";

    public static final String ACCOUNT = """
            {
              "id": 1,
              "iban": "IT60X0542811101000000123456",
              "currency": "EUR",
              "balance": 850.00,
              "status": "ACTIVE",
              "customerId": "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01",
              "createdAt": "2026-11-25T10:00:00Z"
            }""";

    public static final String BLOCKED_ACCOUNT = """
            {
              "id": 1,
              "iban": "IT60X0542811101000000123456",
              "currency": "EUR",
              "balance": 850.00,
              "status": "BLOCKED",
              "customerId": "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01",
              "createdAt": "2026-11-25T10:00:00Z"
            }""";

    public static final String ACCOUNT_LIST = """
            [
              {
                "id": 1,
                "iban": "IT60X0542811101000000123456",
                "currency": "EUR",
                "balance": 850.00,
                "status": "ACTIVE",
                "customerId": "3f1c2a7e-8b4d-4c1e-9a5f-2d6b7e8c9f01",
                "createdAt": "2026-11-25T10:00:00Z"
              }
            ]""";

    public static final String TRANSACTION_PAGE = """
            {
              "content": [
                {
                  "id": 3,
                  "type": "TRANSFER_OUT",
                  "amount": 150.00,
                  "balanceAfter": 850.00,
                  "description": "Rent",
                  "transferId": "9b2e4f6a-1c3d-4e5f-8a7b-0c1d2e3f4a5b",
                  "createdAt": "2026-11-25T11:00:00Z"
                },
                {
                  "id": 2,
                  "type": "WITHDRAW",
                  "amount": 100.00,
                  "balanceAfter": 1000.00,
                  "description": "ATM",
                  "transferId": null,
                  "createdAt": "2026-11-25T10:45:00Z"
                },
                {
                  "id": 1,
                  "type": "DEPOSIT",
                  "amount": 1100.00,
                  "balanceAfter": 1100.00,
                  "description": "Salary",
                  "transferId": null,
                  "createdAt": "2026-11-25T10:30:00Z"
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 3,
              "totalPages": 1,
              "last": true
            }""";

    public static final String TRANSFER = """
            {
              "id": "9b2e4f6a-1c3d-4e5f-8a7b-0c1d2e3f4a5b",
              "fromIban": "IT60X0542811101000000123456",
              "toIban": "IT60X0542811101000000654321",
              "amount": 150.00,
              "description": "Rent",
              "status": "COMPLETED",
              "createdAt": "2026-11-25T11:00:00Z"
            }""";

    // ---------------------------------------------------------------- error bodies (RFC 7807)

    public static final String VALIDATION_ERROR_REGISTER = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "Field errors: {taxCode=Invalid tax code format, password=size must be between 8 and 2147483647}",
              "instance": "/api/v1/auth/register",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T09:00:00Z",
              "errors": [
                { "field": "taxCode", "message": "Invalid tax code format" },
                { "field": "password", "message": "size must be between 8 and 2147483647" }
              ]
            }""";

    public static final String VALIDATION_ERROR_AMOUNT = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "Field errors: {amount=must be greater than or equal to 0.01}",
              "instance": "/api/v1/accounts/IT60X0542811101000000123456/deposit",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T10:30:00Z",
              "errors": [
                { "field": "amount", "message": "must be greater than or equal to 0.01" }
              ]
            }""";

    public static final String VALIDATION_ERROR_PAGING = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "Parameter errors: {size=must be less than or equal to 100}",
              "instance": "/api/v1/customers",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T12:00:00Z",
              "errors": [
                { "field": "size", "message": "must be less than or equal to 100" }
              ]
            }""";

    public static final String VALIDATION_ERROR_DATE_RANGE = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "'from' date must not be after 'to' date",
              "instance": "/api/v1/accounts/IT60X0542811101000000123456/transactions",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T12:00:00Z"
            }""";

    public static final String VALIDATION_ERROR_STATUS = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "status must be ACTIVE or BLOCKED",
              "instance": "/api/v1/accounts/IT60X0542811101000000123456/status",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T12:00:00Z"
            }""";

    public static final String VALIDATION_ERROR_SAME_PASSWORD = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "New password must be different from current password",
              "instance": "/api/v1/customers/me/password",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T09:45:00Z"
            }""";

    public static final String VALIDATION_ERROR_IDEMPOTENCY_KEY = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "Idempotency-Key must be a UUID",
              "instance": "/api/v1/transfers",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T11:00:00Z"
            }""";

    public static final String VALIDATION_ERROR_CURRENCY = """
            {
              "type": "https://bonify.example/errors/validation-error",
              "title": "Invalid input",
              "status": 400,
              "detail": "Currency mismatch between source and target accounts",
              "instance": "/api/v1/transfers",
              "code": "VALIDATION_ERROR",
              "timestamp": "2026-11-25T11:00:00Z"
            }""";

    public static final String SAME_ACCOUNT = """
            {
              "type": "https://bonify.example/errors/same-account",
              "title": "Source and destination are the same account",
              "status": 400,
              "detail": "Source and target IBAN must be different",
              "instance": "/api/v1/transfers",
              "code": "SAME_ACCOUNT",
              "timestamp": "2026-11-25T11:00:00Z"
            }""";

    public static final String WRONG_CURRENT_PASSWORD = """
            {
              "type": "https://bonify.example/errors/wrong-current-password",
              "title": "Wrong current password",
              "status": 400,
              "detail": "Current password is incorrect",
              "instance": "/api/v1/customers/me/password",
              "code": "WRONG_CURRENT_PASSWORD",
              "timestamp": "2026-11-25T09:45:00Z"
            }""";

    public static final String UNAUTHORIZED = """
            {
              "type": "https://bonify.example/errors/unauthorized",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Authentication required",
              "instance": "/api/v1/accounts",
              "code": "UNAUTHORIZED",
              "timestamp": "2026-11-25T10:00:00Z"
            }""";

    public static final String INVALID_CREDENTIALS = """
            {
              "type": "https://bonify.example/errors/unauthorized",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Invalid email or password",
              "instance": "/api/v1/auth/login",
              "code": "UNAUTHORIZED",
              "timestamp": "2026-11-25T09:05:00Z"
            }""";

    public static final String INVALID_REFRESH_TOKEN = """
            {
              "type": "https://bonify.example/errors/unauthorized",
              "title": "Unauthorized",
              "status": 401,
              "detail": "Invalid or expired refresh token",
              "instance": "/api/v1/auth/refresh",
              "code": "UNAUTHORIZED",
              "timestamp": "2026-11-25T09:10:00Z"
            }""";

    public static final String FORBIDDEN_ROLE = """
            {
              "type": "https://bonify.example/errors/forbidden",
              "title": "Forbidden",
              "status": 403,
              "detail": "Access Denied",
              "instance": "/api/v1/customers",
              "code": "FORBIDDEN",
              "timestamp": "2026-11-25T12:00:00Z"
            }""";

    public static final String FORBIDDEN_ACCOUNT = """
            {
              "type": "https://bonify.example/errors/forbidden",
              "title": "Forbidden",
              "status": 403,
              "detail": "Access denied to account: IT60X0542811101000000654321",
              "instance": "/api/v1/accounts/IT60X0542811101000000654321",
              "code": "FORBIDDEN",
              "timestamp": "2026-11-25T10:00:00Z"
            }""";

    public static final String FORBIDDEN_TRANSFER_SOURCE = """
            {
              "type": "https://bonify.example/errors/forbidden",
              "title": "Forbidden",
              "status": 403,
              "detail": "Source account does not belong to authenticated customer",
              "instance": "/api/v1/transfers",
              "code": "FORBIDDEN",
              "timestamp": "2026-11-25T11:00:00Z"
            }""";

    public static final String ACCOUNT_NOT_FOUND = """
            {
              "type": "https://bonify.example/errors/not-found",
              "title": "Not found",
              "status": 404,
              "detail": "Account not found for IBAN: IT00Z0000000000000000000000",
              "instance": "/api/v1/accounts/IT00Z0000000000000000000000",
              "code": "NOT_FOUND",
              "timestamp": "2026-11-25T10:00:00Z"
            }""";

    public static final String TRANSFER_NOT_FOUND = """
            {
              "type": "https://bonify.example/errors/not-found",
              "title": "Not found",
              "status": 404,
              "detail": "Transfer not found: 00000000-0000-0000-0000-000000000000",
              "instance": "/api/v1/transfers/00000000-0000-0000-0000-000000000000",
              "code": "NOT_FOUND",
              "timestamp": "2026-11-25T11:05:00Z"
            }""";

    public static final String EMAIL_ALREADY_USED = """
            {
              "type": "https://bonify.example/errors/email-already-used",
              "title": "Email already in use",
              "status": 409,
              "detail": "Email already registered",
              "instance": "/api/v1/auth/register",
              "code": "EMAIL_ALREADY_USED",
              "timestamp": "2026-11-25T09:00:00Z"
            }""";

    public static final String TAX_CODE_ALREADY_USED = """
            {
              "type": "https://bonify.example/errors/tax-code-already-used",
              "title": "Tax code already in use",
              "status": 409,
              "detail": "Tax code already registered",
              "instance": "/api/v1/auth/register",
              "code": "TAX_CODE_ALREADY_USED",
              "timestamp": "2026-11-25T09:00:00Z"
            }""";

    public static final String IDEMPOTENCY_KEY_REUSED = """
            {
              "type": "https://bonify.example/errors/idempotency-key-reused",
              "title": "Idempotency key reused with a different body",
              "status": 409,
              "detail": "Idempotency-Key already used with different transfer payload",
              "instance": "/api/v1/transfers",
              "code": "IDEMPOTENCY_KEY_REUSED",
              "timestamp": "2026-11-25T11:00:00Z"
            }""";

    public static final String INSUFFICIENT_FUNDS = """
            {
              "type": "https://bonify.example/errors/insufficient-funds",
              "title": "Insufficient funds",
              "status": 422,
              "detail": "Account IT60X0542811101000000123456 has 850.00 EUR, 1000.00 EUR requested",
              "instance": "/api/v1/accounts/IT60X0542811101000000123456/withdraw",
              "code": "INSUFFICIENT_FUNDS",
              "timestamp": "2026-11-25T10:45:00Z"
            }""";

    public static final String ACCOUNT_BLOCKED = """
            {
              "type": "https://bonify.example/errors/account-blocked",
              "title": "Account blocked",
              "status": 422,
              "detail": "Account is not ACTIVE",
              "instance": "/api/v1/accounts/IT60X0542811101000000123456/deposit",
              "code": "ACCOUNT_BLOCKED",
              "timestamp": "2026-11-25T10:30:00Z"
            }""";

    public static final String TRANSFER_ACCOUNT_BLOCKED = """
            {
              "type": "https://bonify.example/errors/account-blocked",
              "title": "Account blocked",
              "status": 422,
              "detail": "Both accounts must be ACTIVE",
              "instance": "/api/v1/transfers",
              "code": "ACCOUNT_BLOCKED",
              "timestamp": "2026-11-25T11:00:00Z"
            }""";
}
