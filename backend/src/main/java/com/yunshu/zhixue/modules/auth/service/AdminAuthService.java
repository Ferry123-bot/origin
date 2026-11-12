package com.yunshu.zhixue.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.constant.AppConstants;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.AdminLoginRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.AdminTokenView;
import com.yunshu.zhixue.modules.system.entity.AdminUser;
import com.yunshu.zhixue.modules.system.entity.OperationLog;
import com.yunshu.zhixue.modules.system.mapper.AdminUserMapper;
import com.yunshu.zhixue.modules.system.mapper.OperationLogMapper;
import com.yunshu.zhixue.security.AuthRateLimiter;
import com.yunshu.zhixue.security.JwtService;
import com.yunshu.zhixue.security.LoginGuard;
import com.yunshu.zhixue.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final String AUDIENCE = JwtService.AUDIENCE_ADMIN;

    private final AdminUserMapper adminUserMapper;
    private final OperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginGuard loginGuard;
    private final AuthRateLimiter rateLimiter;
    private final String dummyHash;

    public AdminAuthService(AdminUserMapper adminUserMapper, OperationLogMapper operationLogMapper,
                             PasswordEncoder passwordEncoder, TokenService tokenService, LoginGuard loginGuard,
                             AuthRateLimiter rateLimiter) {
        this.adminUserMapper = adminUserMapper;
        this.operationLogMapper = operationLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginGuard = loginGuard;
        this.rateLimiter = rateLimiter;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional
    public AdminTokenView login(AdminLoginRequest request, String ip) {
        String account = request.account().trim();
        rateLimiter.check("admin-login", ip, account);
        loginGuard.assertNotLocked(AUDIENCE, account, ErrorCode.ADMIN_LOCKED);
        AdminUser user = findByAccount(account);
        boolean matched = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matched) {
            loginGuard.onFailure(AUDIENCE, account, ErrorCode.ADMIN_LOCKED);
            throw new BizException(ErrorCode.ADMIN_BAD_CREDENTIALS);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        loginGuard.onSuccess(AUDIENCE, account);
        adminUserMapper.update(null, Wrappers.<AdminUser>lambdaUpdate()
                .eq(AdminUser::getId, user.getId())
                .set(AdminUser::getLastLoginAt, LocalDateTime.now(ZoneOffset.UTC)));
        writeLog(user.getId(), user.getUsername(), "登录", ip);
        return tokensOf(user);
    }

    public AdminTokenView refresh(String refreshToken) {
        long userId = tokenService.consumeRefresh(AUDIENCE, refreshToken);
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        return tokensOf(user);
    }

    public void logout(String authorization, String refreshToken, String ip) {
        Long userId = tokenService.readAccessUserId(AUDIENCE, authorization);
        tokenService.revoke(AUDIENCE, authorization, refreshToken);
        if (userId == null) {
            return;
        }
        AdminUser user = adminUserMapper.selectById(userId);
        if (user != null) {
            writeLog(user.getId(), user.getUsername(), "登出", ip);
        }
    }

    private AdminTokenView tokensOf(AdminUser user) {
        TokenService.TokenPair pair = tokenService.issue(AUDIENCE, user.getId());
        String roleCode = adminUserMapper.findRoleCode(user.getRoleId());
        List<String> permissions = adminUserMapper.findPermissionCodes(user.getRoleId());
        return new AdminTokenView(pair.accessToken(), pair.refreshToken(), pair.expiresIn(),
                user.getUsername(), roleCode == null ? "" : roleCode,
                permissions == null ? List.of() : permissions);
    }

    private AdminUser findByAccount(String account) {
        AdminUser byName = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, account));
        if (byName != null) {
            return byName;
        }
        if (!account.matches(AppConstants.MOBILE_PATTERN)) {
            return null;
        }
        return adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getMobile, account));
    }

    private void writeLog(Long userId, String username, String action, String ip) {
        try {
            OperationLog row = new OperationLog();
            row.setUserId(userId);
            row.setUsername(username);
            row.setAction(action);
            row.setTargetType("账号");
            row.setTargetName(username);
            row.setIp(ip == null || ip.isBlank() ? "0.0.0.0" : ip);
            row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            operationLogMapper.insert(row);
        } catch (RuntimeException ex) {
            log.warn("写入操作日志失败 action={} userId={}", action, userId);
        }
    }
}
