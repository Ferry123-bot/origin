package com.yunshu.zhixue.security;

import com.yunshu.zhixue.common.constant.AppConstants;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class AuthRateLimiter {

    private final StringRedisTemplate redis;

    public AuthRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String action, String ip, String account) {
        hit("rate:" + action + ":ip:" + safe(ip));
        if (account != null && !account.isBlank()) {
            hit("rate:" + action + ":acct:" + safe(account.trim().toLowerCase(Locale.ROOT)));
        }
    }

    private void hit(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofMinutes(1));
        }
        if (count != null && count > AppConstants.AUTH_RATE_PER_MINUTE) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }
}
