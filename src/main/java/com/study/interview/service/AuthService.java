package com.study.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.interview.exception.BizException;
import com.study.interview.mapper.UserAccountMapper;
import com.study.interview.model.UserAccount;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Long register(String username, String rawPassword) {
        String accountName = requireAccountName(username);
        requirePassword(rawPassword);

        UserAccount account = new UserAccount();
        account.setUsername(accountName);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        try {
            if (userAccountMapper.insert(account) != 1) {
                throw new BizException(2001, HttpStatus.INTERNAL_SERVER_ERROR, "账号保存失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(1003, HttpStatus.CONFLICT, "用户名已被使用");
        }
        return account.getId();
    }

    public Long verifyCredentials(String username, String rawPassword) {
        String accountName = requireAccountName(username);
        requirePassword(rawPassword);
        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getUsername, accountName)
        );
        if (userAccount == null || !passwordEncoder.matches(rawPassword, userAccount.getPasswordHash())) {
            throw new BizException(1004, HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return userAccount.getId();
    }

    private String requireAccountName(String username) {
        if (username == null || username.isBlank()) {
            throw new BizException(1001, "账号名不能为空");
        }
        String accountName = username.strip();
        if (accountName.codePointCount(0, accountName.length()) > 64) {
            throw new BizException(1001, "账号名最多 64 个字符");
        }
        return accountName;
    }

    private void requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(1001, "密码不能为空");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BizException(1001, "密码最多 72 个 UTF-8 字节");
        }
    }
}
