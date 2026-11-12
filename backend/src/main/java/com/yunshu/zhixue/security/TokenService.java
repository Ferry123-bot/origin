package com.yunshu.zhixue.security;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    private final JwtService jwtService;
    private final StringRedisTemplate redis;

    public TokenService(JwtService jwtService, StringRedisTemplate redis) {
        this.jwtService = jwtService;
        this.redis = redis;
    }

    public TokenPair issue(String audience, long userId) {
        String subject = Long.toString(userId);
        IssuedToken access = jwtService.createAccessToken(audience, subject);
        IssuedToken refresh = jwtService.createRefreshToken(audience, subject);
        Duration ttl = Duration.between(Instant.now(), refresh.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        redis.opsForValue().set(refreshKey(audience, refresh.jti()), subject, ttl);
        long expiresIn = Math.max(1, Duration.between(Instant.now(), access.expiresAt()).toSeconds());
        return new TokenPair(access.token(), refresh.token(), expiresIn);
    }

    public long consumeRefresh(String audience, String refreshToken) {
        Claims claims = parseRefresh(refreshToken, audience);
        String stored = redis.opsForValue().getAndDelete(refreshKey(audience, claims.getId()));
        if (stored == null || !stored.equals(claims.getSubject())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(stored);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    public void revoke(String audience, String authorization, String refreshToken) {
        String access = bearer(authorization);
        if (access != null) {
            Claims claims = parseQuiet(access, audience);
            if (claims != null && JwtService.TYPE_ACCESS.equals(claims.get("typ")) && claims.getId() != null
                    && claims.getExpiration() != null) {
                long seconds = claims.getExpiration().toInstant().getEpochSecond() - Instant.now().getEpochSecond();
                if (seconds > 0) {
                    redis.opsForValue().set(blacklistKey(claims.getId()), "1", Duration.ofSeconds(seconds));
                }
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            Claims claims = parseQuiet(refreshToken.trim(), audience);
            if (claims != null && JwtService.TYPE_REFRESH.equals(claims.get("typ")) && claims.getId() != null) {
                redis.delete(refreshKey(audience, claims.getId()));
            }
        }
    }

    public void revokeAll(String audience, long userId, String authorization, String refreshToken) {
        revoke(audience, authorization, refreshToken);
        String subject = Long.toString(userId);
        ScanOptions options = ScanOptions.scanOptions().match(refreshKey(audience, "*")).count(100).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (subject.equals(redis.opsForValue().get(key))) {
                    redis.delete(key);
                }
            }
        }
        redis.opsForValue().set(cutKey(audience, subject), Long.toString(Instant.now().getEpochSecond()), Duration.ofDays(14));
    }

    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(blacklistKey(jti)));
    }

    public boolean issuedBeforeCut(String audience, String subject, Instant issuedAt) {
        if (subject == null || issuedAt == null) {
            return false;
        }
        String raw = redis.opsForValue().get(cutKey(audience, subject));
        if (raw == null) {
            return false;
        }
        try {
            return issuedAt.getEpochSecond() < Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public Long readAccessUserId(String audience, String authorization) {
        String access = bearer(authorization);
        if (access == null) {
            return null;
        }
        Claims claims = parseQuiet(access, audience);
        if (claims == null || !JwtService.TYPE_ACCESS.equals(claims.get("typ")) || claims.getSubject() == null) {
            return null;
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Claims parseRefresh(String refreshToken, String audience) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Claims claims = parseQuiet(refreshToken.trim(), audience);
        if (claims == null || !JwtService.TYPE_REFRESH.equals(claims.get("typ")) || claims.getId() == null
                || claims.getSubject() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return claims;
    }

    private Claims parseQuiet(String token, String audience) {
        try {
            return jwtService.parse(token, audience);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private String bearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = value.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private String refreshKey(String audience, String jti) {
        return "token:refresh:" + audience + ":" + jti;
    }

    private String blacklistKey(String jti) {
        return "token:blacklist:" + jti;
    }

    private String cutKey(String audience, String subject) {
        return "token:cut:" + audience + ":" + subject;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }
}
