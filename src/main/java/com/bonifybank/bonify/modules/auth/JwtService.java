package com.bonifybank.bonify.modules.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.bonifybank.bonify.config.JwtConfig;
import com.bonifybank.bonify.config.JwtProperties;
import com.bonifybank.bonify.modules.customer.CustomerEntity;

/**
 * Issues access tokens. Validation is done by Spring Security (JwtDecoder in JwtConfig), not here.
 * Claims:
 * <ul>
 *   <li>sub  - customer id (not the email, so the token survives an email change)</li>
 *   <li>role - CUSTOMER / ADMIN, mapped to ROLE_* authorities by JwtConfig</li>
 *   <li>iss, iat, exp - standard claims, validated by the JwtDecoder</li>
 *   <li>jti  - unique token id, useful for logging and a future revocation list</li>
 * </ul>
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this(jwtEncoder, properties, Clock.systemUTC());
    }

    // Visible for tests: lets them issue already expired tokens
    JwtService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Returns the signed token (compact form), to be sent as "Authorization: Bearer &lt;token&gt;". */
    public String generateToken(CustomerEntity customer) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(customer.getId().toString())
                .claim(JwtConfig.ROLE_CLAIM, customer.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiration()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Token validity in seconds: the "expiresIn" field of the login response (FR-AUTH-02). */
    public long getExpirationSeconds() {
        return properties.expiration().toSeconds();
    }
}
