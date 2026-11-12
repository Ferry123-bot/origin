package com.yunshu.zhixue.security;

import com.yunshu.zhixue.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String AUDIENCE_PORTAL = "portal";
    public static final String AUDIENCE_ADMIN = "admin";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final AppProperties properties;
    private final SecretKey portalKey;
    private final SecretKey adminKey;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.portalKey = Keys.hmacShaKeyFor(properties.getJwt().getPortalSecret().getBytes(StandardCharsets.UTF_8));
        this.adminKey = Keys.hmacShaKeyFor(properties.getJwt().getAdminSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken createAccessToken(String audience, String subject) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getJwt().getAccessTokenMinutes() * 60);
        return build(audience, subject, TYPE_ACCESS, now, exp);
    }

    public IssuedToken createRefreshToken(String audience, String subject) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getJwt().getRefreshTokenDays() * 24 * 60 * 60);
        return build(audience, subject, TYPE_REFRESH, now, exp);
    }

    public Claims parse(String token, String audience) {
        return Jwts.parser()
                .verifyWith(keyOf(audience))
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private IssuedToken build(String audience, String subject, String type, Instant issuedAt, Instant expiresAt) {
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(subject)
                .claim("typ", type)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(keyOf(audience))
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    private SecretKey keyOf(String audience) {
        if (AUDIENCE_ADMIN.equals(audience)) {
            return adminKey;
        }
        return portalKey;
    }
}
