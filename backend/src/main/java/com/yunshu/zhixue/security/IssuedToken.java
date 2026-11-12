package com.yunshu.zhixue.security;

import java.time.Instant;

public record IssuedToken(String token, String jti, Instant expiresAt) {
}
