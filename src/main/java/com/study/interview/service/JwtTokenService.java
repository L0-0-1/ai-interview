package com.study.interview.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Service
public class JwtTokenService {

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenService(@Value("${APP_JWT_SECRET_BASE64:}") String encodedSecret) {
        if (encodedSecret.isBlank()) {
            encoder = null;
            decoder = token -> { throw new BadJwtException("JWT 验签密钥未配置"); };
            return;
        }

        SecretKeySpec key = readSigningKey(encodedSecret);
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtClaimValidator<Instant>("exp", Objects::nonNull),
                new JwtClaimValidator<String>("sub", JwtTokenService::isUserId)
        ));
        decoder = jwtDecoder;
    }

    public String issue(Long userId) {
        if (encoder == null) {
            throw new IllegalStateException("请配置 APP_JWT_SECRET_BASE64 后再签发 JWT");
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt verify(String token) {
        return decoder.decode(token);
    }

    private SecretKeySpec readSigningKey(String encodedSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedSecret.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("APP_JWT_SECRET_BASE64 必须是有效的 Base64");
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("HS256 签名密钥解码后至少需要 32 字节");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private static boolean isUserId(String subject) {
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
