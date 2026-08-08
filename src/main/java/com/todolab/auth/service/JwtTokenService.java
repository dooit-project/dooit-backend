package com.todolab.auth.service;

import com.todolab.Constant;
import com.todolab.auth.config.AuthJwtProperties;
import com.todolab.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthJwtProperties authJwtProperties;

    public AccessToken createAccessToken(User user) {
        return createAccessToken(user, authJwtProperties.accessTokenTtl());
    }

    public AccessToken createGuestAccessToken(User user) {
        return createAccessToken(user, authJwtProperties.guestAccessTokenTtl());
    }

    public Duration guestAccessTokenTtl() {
        return authJwtProperties.guestAccessTokenTtl();
    }

    private AccessToken createAccessToken(User user, Duration tokenTtl) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenTtl);

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(authJwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim("accountType", user.getAccountType().name())
                .claim("role", user.getRole().name());

        if (user.getEmail() != null) {
            claimsBuilder.claim("email", user.getEmail());
        }
        if (user.getDisplayName() != null) {
            claimsBuilder.claim("displayName", user.getDisplayName());
        }

        JwtClaimsSet claims = claimsBuilder.build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();

        return new AccessToken(tokenValue, LocalDateTime.ofInstant(expiresAt, Constant.ZONE));
    }

    public record AccessToken(String tokenValue, LocalDateTime expiresAt) {
    }
}
