package com.bonifybank.bonify.config;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * FR-PLT-01: OpenAPI documentation served by springdoc at /v3/api-docs and rendered by Swagger UI
 * at /swagger-ui.html.
 *
 * Declares the bearer scheme, so Swagger UI shows the "Authorize" button and sends the access token
 * returned by login in the Authorization header of every call. The description below is the quick
 * start shown at the top of Swagger UI; per-endpoint docs and examples live on the controllers, the
 * DTOs (@Schema) and {@link ApiExamples}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Bonify API", version = "v1", description = OpenApiConfig.DESCRIPTION),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME),
        // Declared here to fix the order of the groups in Swagger UI (the natural flow of a client)
        tags = {
                @Tag(name = OpenApiConfig.TAG_AUTH, description = "Registration, login and session management. "
                        + "Public endpoints: no token required."),
                @Tag(name = OpenApiConfig.TAG_CUSTOMERS, description = "Own profile and password; customer list "
                        + "for the admin."),
                @Tag(name = OpenApiConfig.TAG_ACCOUNTS, description = "EUR current accounts: open, read, deposit, "
                        + "withdraw, block/unblock."),
                @Tag(name = OpenApiConfig.TAG_TRANSACTIONS, description = "Paged movement history of an account."),
                @Tag(name = OpenApiConfig.TAG_TRANSFERS, description = "Atomic and idempotent transfers between "
                        + "accounts.")
        })
@SecurityScheme(name = OpenApiConfig.BEARER_SCHEME, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT", description = "The `accessToken` returned by `POST /api/v1/auth/login` "
                + "(paste it without the `Bearer ` prefix). It expires after 5 minutes: use "
                + "`POST /api/v1/auth/refresh` to get a new one.")
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    public static final String TAG_AUTH = "Auth";
    public static final String TAG_CUSTOMERS = "Customers";
    public static final String TAG_ACCOUNTS = "Accounts";
    public static final String TAG_TRANSACTIONS = "Transactions";
    public static final String TAG_TRANSFERS = "Transfers";

    static final String DESCRIPTION = """
            REST API of a simplified retail bank: customers, EUR current accounts, deposits, withdrawals,
            transfers and transaction history.

            ## Quick start (Swagger UI)

            1. **Register** with `POST /api/v1/auth/register` (the example body is already valid).
            2. **Log in** with `POST /api/v1/auth/login` and copy the `accessToken` from the response.
            3. Click **Authorize** (top right), paste the token and confirm: every call now sends
               `Authorization: Bearer <token>`.
            4. **Open an account** with `POST /api/v1/accounts` (no body) and copy the `iban`.
            5. **Deposit** with `POST /api/v1/accounts/{iban}/deposit`.
            6. **Send a transfer** with `POST /api/v1/transfers` to the IBAN of another customer,
               with a fresh UUID in the `Idempotency-Key` header.
            7. **Check the history** with `GET /api/v1/accounts/{iban}/transactions`.

            The access token lasts **5 minutes**. When a call returns 401, get a new pair with
            `POST /api/v1/auth/refresh` and click **Authorize** again. The refresh token is rotated on
            every use: always keep the last one.

            ## Roles

            | Role | Can do |
            |---|---|
            | `CUSTOMER` | Everything on their own profile, accounts and transfers |
            | `ADMIN` | Read any account, its history and any transfer; list customers; block/unblock accounts |

            The admin account is created directly in the database: registration always creates a `CUSTOMER`.

            ## Conventions

            - **Versioning**: the version is in the path, `/api/v1/...`.
            - **Amounts**: JSON numbers with at most 2 decimals, minimum `0.01`. Currency is always `EUR`.
            - **Dates and times**: ISO 8601, UTC (`2026-11-25T10:00:00Z`); query dates as `YYYY-MM-DD`.
            - **Pagination**: `page` (from 0) and `size` (1-100, default 20); results are newest first.
            - **Idempotency**: `POST /transfers` requires an `Idempotency-Key` (UUID). Retrying with the
              same key and body returns the original transfer (200) without moving money twice.

            ## Errors

            Every error is an [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) problem detail
            (`application/problem+json`) with a stable `code` to switch on in the client:

            | HTTP | `code` | When |
            |---|---|---|
            | 400 | `VALIDATION_ERROR` | Invalid body, parameter or header; `errors` lists the fields |
            | 400 | `SAME_ACCOUNT` | Transfer to the source account |
            | 400 | `WRONG_CURRENT_PASSWORD` | Password change with the wrong current password |
            | 401 | `UNAUTHORIZED` | Missing, invalid or expired token; wrong credentials |
            | 403 | `FORBIDDEN` | Wrong role, or the resource belongs to someone else |
            | 404 | `NOT_FOUND` | Unknown IBAN, transfer or path |
            | 405 | `METHOD_NOT_ALLOWED` | HTTP method not supported on the path |
            | 409 | `EMAIL_ALREADY_USED`, `TAX_CODE_ALREADY_USED` | Duplicate registration data |
            | 409 | `IDEMPOTENCY_KEY_REUSED` | Same `Idempotency-Key` with a different body |
            | 415 | `UNSUPPORTED_MEDIA_TYPE` | Body not sent as `application/json` |
            | 422 | `INSUFFICIENT_FUNDS` | Balance lower than the amount |
            | 422 | `ACCOUNT_BLOCKED` | Operation on a `BLOCKED` account |
            | 500 | `INTERNAL_ERROR` | Unexpected error (details are only in the server log) |
            """;

    /**
     * Adds the 401 response to every protected operation, so it does not have to be repeated on each
     * controller method. Public operations (the auth ones) declare an empty security list and are skipped.
     */
    @Bean
    OpenApiCustomizer unauthorizedResponseCustomizer() {
        ApiResponse unauthorized = new ApiResponse()
                .description("Missing, invalid or expired access token")
                .content(new Content().addMediaType(ApiExamples.PROBLEM_JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                        .example(parse(ApiExamples.UNAUTHORIZED))));
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().stream()
                .filter(OpenApiConfig::isProtected)
                .forEach(operation -> operation.getResponses().putIfAbsent("401", unauthorized)));
    }

    // No operation-level security means the global bearer requirement applies
    private static boolean isProtected(Operation operation) {
        List<io.swagger.v3.oas.models.security.SecurityRequirement> security = operation.getSecurity();
        return security == null || !security.isEmpty();
    }

    private static Object parse(String json) {
        try {
            return Json.mapper().readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid OpenAPI example", ex);
        }
    }
}
