package com.study.interview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.study.interview.controller.AuthController;
import com.study.interview.exception.GlobalExceptionHandler;
import com.study.interview.mapper.UserAccountMapper;
import com.study.interview.model.UserAccount;
import com.study.interview.service.AuthService;
import com.study.interview.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTests {

    private final UserAccountMapper mapper = mock(UserAccountMapper.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private JwtTokenService tokenService;
    private NimbusJwtDecoder tokenDecoder;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        tokenService = spy(new JwtTokenService(Base64.getEncoder().encodeToString(key)));
        tokenDecoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).build();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(new AuthService(mapper, encoder), tokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsCorrectPasswordAndReturnsSignedToken() throws Exception {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingAccount());

        mvc.perform(credentials("login", "sample-test-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(result -> {
                    String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
                    assertThat(tokenDecoder.decode(token).getSubject()).isEqualTo("7");
                })
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void wrongPasswordAndUnknownAccountHaveTheSameResponse(boolean accountExists) throws Exception {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(accountExists ? existingAccount() : null);

        mvc.perform(credentials("login", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
        verifyNoInteractions(tokenService);
    }

    @Test
    void registrationSavesHashAndReturnsOnlyUserId() throws Exception {
        when(mapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(7L);
            return 1;
        });

        mvc.perform(credentials("register", "sample-test-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verifyNoInteractions(tokenService);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("demo-user");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("sample-test-password");
        assertThat(encoder.matches("sample-test-password", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registrationMapsDuplicateUsernameToConflict() throws Exception {
        when(mapper.insert(any(UserAccount.class))).thenThrow(new DuplicateKeyException("duplicate test username"));

        mvc.perform(credentials("register", "sample-test-password"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("用户名已被使用"));
    }

    private UserAccount existingAccount() {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setUsername("demo-user");
        account.setPasswordHash(encoder.encode("sample-test-password"));
        return account;
    }

    private MockHttpServletRequestBuilder credentials(String endpoint, String password) {
        return post("/api/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"demo-user","password":"%s"}
                """.formatted(password));
    }
}
