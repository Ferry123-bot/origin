package com.yunshu.zhixue.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.PasswordLoginRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.PortalTokenView;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.RegisterRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.ResetPasswordRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.ResetTokenView;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.SmsCodeRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.SmsCodeView;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.SmsLoginRequest;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import com.yunshu.zhixue.security.AuthRateLimiter;
import com.yunshu.zhixue.security.JwtService;
import com.yunshu.zhixue.security.LoginGuard;
import com.yunshu.zhixue.security.TokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class PortalAuthService {

    private static final String AUDIENCE = JwtService.AUDIENCE_PORTAL;
    private static final String RESET_PREFIX = "reset:token:";

    private final SmsCodeService smsCodeService;
    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginGuard loginGuard;
    private final AuthRateLimiter rateLimiter;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final String dummyHash;

    public PortalAuthService(SmsCodeService smsCodeService, StudentMapper studentMapper, PasswordEncoder passwordEncoder,
                              TokenService tokenService, LoginGuard loginGuard, AuthRateLimiter rateLimiter,
                              org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.smsCodeService = smsCodeService;
        this.studentMapper = studentMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginGuard = loginGuard;
        this.rateLimiter = rateLimiter;
        this.redis = redis;
        this.dummyHash = passwordEncoder.encode("dummy-password");
    }

    public SmsCodeView sendSms(SmsCodeRequest request, String ip) {
        rateLimiter.check("portal-sms", ip, request.mobile());
        return smsCodeService.send(request.mobile(), request.type());
    }

    @Transactional
    public PortalTokenView register(RegisterRequest request, String ip) {
        rateLimiter.check("portal-register", ip, request.mobile());
        smsCodeService.verify(SmsCodeService.REGISTER, request.mobile(), request.code());
        Student student = new Student();
        student.setMobile(request.mobile());
        student.setPasswordHash(passwordEncoder.encode(request.password()));
        student.setNickname("用户" + request.mobile().substring(request.mobile().length() - 4));
        student.setGender(0);
        student.setStatus(1);
        student.setGuideFinished(0);
        try {
            studentMapper.insert(student);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.CONTACT_TAKEN, "手机号已注册");
        }
        return tokensOf(student);
    }

    @Transactional
    public PortalTokenView loginByPassword(PasswordLoginRequest request, String ip) {
        String account = request.account().trim();
        rateLimiter.check("portal-login", ip, account);
        loginGuard.assertNotLocked(AUDIENCE, account, ErrorCode.ACCOUNT_LOCKED);
        Student student = findByAccount(account);
        boolean matched = passwordEncoder.matches(request.password(), student == null ? dummyHash : student.getPasswordHash());
        if (student == null || !matched) {
            loginGuard.onFailure(AUDIENCE, account, ErrorCode.ACCOUNT_LOCKED);
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        assertEnabled(student);
        loginGuard.onSuccess(AUDIENCE, account);
        touchLogin(student.getId());
        return tokensOf(student);
    }

    @Transactional
    public PortalTokenView loginBySms(SmsLoginRequest request, String ip) {
        rateLimiter.check("portal-login", ip, request.mobile());
        smsCodeService.verify(SmsCodeService.LOGIN, request.mobile(), request.code());
        Student student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getMobile, request.mobile()));
        if (student == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "手机号未注册");
        }
        assertEnabled(student);
        loginGuard.onSuccess(AUDIENCE, request.mobile());
        touchLogin(student.getId());
        return tokensOf(student);
    }

    public ResetTokenView verifyReset(com.yunshu.zhixue.modules.auth.dto.AuthPayloads.ResetVerifyRequest request, String ip) {
        rateLimiter.check("portal-reset", ip, request.mobile());
        smsCodeService.verify(SmsCodeService.RESET, request.mobile(), request.code());
        Student student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getMobile, request.mobile()));
        if (student == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "手机号未注册");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(RESET_PREFIX + token, student.getId().toString(),
                Duration.ofMinutes(com.yunshu.zhixue.common.constant.AppConstants.RESET_TOKEN_TTL_MINUTES));
        return new ResetTokenView(token);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ip) {
        rateLimiter.check("portal-reset", ip, request.resetToken());
        String key = RESET_PREFIX + request.resetToken().trim();
        String studentId = redis.opsForValue().getAndDelete(key);
        if (studentId == null) {
            throw new BizException(ErrorCode.CONFLICT, "重置凭证已失效，请重新验证");
        }
        Student student = studentMapper.selectById(Long.valueOf(studentId));
        if (student == null) {
            throw new BizException(ErrorCode.CONFLICT, "重置凭证已失效，请重新验证");
        }
        studentMapper.update(null, Wrappers.<Student>lambdaUpdate()
                .eq(Student::getId, student.getId())
                .set(Student::getPasswordHash, passwordEncoder.encode(request.newPassword())));
        loginGuard.onSuccess(AUDIENCE, student.getMobile());
    }

    public PortalTokenView refresh(String refreshToken) {
        long userId = tokenService.consumeRefresh(AUDIENCE, refreshToken);
        Student student = studentMapper.selectById(userId);
        if (student == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        assertEnabled(student);
        return tokensOf(student);
    }

    public void logout(String authorization, String refreshToken) {
        tokenService.revoke(AUDIENCE, authorization, refreshToken);
    }

    private PortalTokenView tokensOf(Student student) {
        TokenService.TokenPair pair = tokenService.issue(AUDIENCE, student.getId());
        return new PortalTokenView(pair.accessToken(), pair.refreshToken(), pair.expiresIn(), student.getNickname());
    }

    private Student findByAccount(String account) {
        if (account.matches(com.yunshu.zhixue.common.constant.AppConstants.MOBILE_PATTERN)) {
            return studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getMobile, account));
        }
        List<Student> found = studentMapper.selectList(Wrappers.<Student>lambdaQuery()
                .eq(Student::getEmail, account)
                .last("LIMIT 2"));
        if (found.size() != 1) {
            return null;
        }
        return found.get(0);
    }

    private void assertEnabled(Student student) {
        if (student.getStatus() != null && student.getStatus() == 0) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private void touchLogin(Long studentId) {
        studentMapper.update(null, Wrappers.<Student>lambdaUpdate()
                .eq(Student::getId, studentId)
                .set(Student::getLastLoginAt, LocalDateTime.now(ZoneOffset.UTC)));
    }
}
