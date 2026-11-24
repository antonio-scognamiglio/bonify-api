package com.bonifybank.bonify.modules.auth.dto;

import java.util.UUID;

/**
 * Customer profile as returned by register (FR-AUTH-01) and, later, /customers/me (FR-CUS-01).
 * The password hash is never exposed.
 */
public record CustomerProfile(UUID id, String firstName, String lastName, String email) {
}
