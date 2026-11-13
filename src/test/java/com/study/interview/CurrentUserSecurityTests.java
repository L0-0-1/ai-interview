package com.study.interview;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.study.interview.config.SecurityConfig;
import com.study.interview.controller.AuthController;
import com.study.interview.service.AuthService;
import com.study.interview.service.JwtTokenService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(CurrentUserSecurityTests.WebConfig.class)
class CurrentUserSecurityTests {

    private static final byte[] SIGNING_KEY = randomKey();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilter;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private AuthService authService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test
    void returnsUserIdFromVerifiedToken() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7));
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenSignedByAnotherKey() throws Exception {
        var otherIssuer = new JwtTokenService(Base64.getEncoder().encodeToString(randomKey()));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherIssuer.issue(7L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredTokenEvenWithValidSignature() throws Exception {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject("7")
                .issuedAt(now.minusSeconds(1801))
                .expiresAt(now.minusSeconds(1))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SIGNING_KEY));
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRemainsAvailableWithoutToken() throws Exception {
        when(authService.verifyCredentials("demo-user", "demo-password")).thenReturn(7L);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"demo-user","password":"demo-password"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthController.class})
    static class WebConfig {

        @Bean
        AuthService authService() {
            return mock(AuthService.class);
        }

        @Bean
        JwtTokenService tokenService() {
            return new JwtTokenService(Base64.getEncoder().encodeToString(SIGNING_KEY));
        }
    }
}
