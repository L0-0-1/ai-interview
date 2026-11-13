package com.study.interview.controller;

import com.study.interview.common.Result;
import com.study.interview.controller.dto.AccountResult;
import com.study.interview.controller.dto.AuthRequest;
import com.study.interview.controller.dto.LoginResult;
import com.study.interview.service.AuthService;
import com.study.interview.service.JwtTokenService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthService authService, JwtTokenService jwtTokenService) {
        this.authService = authService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/register")
    public Result<AccountResult> register(@RequestBody AuthRequest request) {
        Long userId = authService.register(request.username(), request.password());
        return Result.success(new AccountResult(userId));
    }

    @PostMapping("/login")
    public Result<LoginResult> login(@RequestBody AuthRequest request) {
        Long userId = authService.verifyCredentials(request.username(), request.password());
        String token = jwtTokenService.issue(userId);
        return Result.success(new LoginResult(userId, token));
    }

    @GetMapping("/me")
    public Result<AccountResult> me(@AuthenticationPrincipal Jwt jwt) {
        return Result.success(new AccountResult(Long.valueOf(jwt.getSubject())));
    }
}
