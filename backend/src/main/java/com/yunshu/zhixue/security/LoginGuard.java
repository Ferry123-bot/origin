package com.yunshu.zhixue.security;

import com.yunshu.zhixue.common.constant.AppConstants;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class LoginGuard {

    private final StringRedisTemplate redis;

    public LoginGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void assertNotLocked(String audience, String account, ErrorCode locked) {
        String key = key(audience, account);
        String value = redis.opsForValue().get(key);
        if (value != null && parse(value) >= AppConstants.LOGIN_FAIL_LOCK_THRESHOLD) {
            throw locked(key, locked);
        }
    }

    public void onFailure(String audience, String account, ErrorCode locked) {
        String key = key(audience, account);
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return;
        }
        if (count == 1) {
            redis.expire(key, Duration.ofMinutes(AppConstants.LOGIN_LOCK_MINUTES));
        }
        if (count >= AppConstants.LOGIN_FAIL_LOCK_THRESHOLD) {
            if (count == AppConstants.LOGIN_FAIL_LOCK_THRESHOLD) {
                redis.expire(key, Duration.ofMinutes(AppConstants.LOGIN_LOCK_MINUTES));
            }
            throw locked(key, locked);
        }
    }

    public void onSuccess(String audience, String account) {
        redis.delete(key(audience, account));
    }

    private BizException locked(String key, ErrorCode locked) {
        Long seconds = redis.getExpire(key);
        long minutes = 1;
        if (seconds != null && seconds > 0) {
            minutes = Math.max(1, (seconds + 59) / 60);
        }
        return new BizException(locked, "账号已锁定，请 " + minutes + " 分钟后重试");
    }

    private String key(String audience, String account) {
        String normalized = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120);
        }
        return "login:fail:" + audience + ":" + normalized;
    }

    private int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
