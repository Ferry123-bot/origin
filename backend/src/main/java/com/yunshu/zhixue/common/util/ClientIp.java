package com.yunshu.zhixue.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return trim(first);
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : trim(remote);
    }

    private static String trim(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
