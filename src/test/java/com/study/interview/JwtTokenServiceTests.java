package com.study.interview;

import com.study.interview.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTests {

    @Test
    void issuesSignedTokenWithUserIdAndExpiration() {
        byte[] key = testKey();
        var service = new JwtTokenService(Base64.getEncoder().encodeToString(key));

        String token = service.issue(7L);
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).build();
        var jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void tokenFailsVerificationWithAnotherKey() {
        var service = new JwtTokenService(Base64.getEncoder().encodeToString(testKey()));
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(testKey(), "HmacSHA256")).build();

        assertThatThrownBy(() -> decoder.decode(service.issue(7L))).isInstanceOf(JwtException.class);
    }

    private byte[] testKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
