package com.yunshu.zhixue.modules.route.service;

final class RouteSupport {

    private RouteSupport() {
    }

    static int minutes(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    static String durationText(int minutes) {
        int hours = minutes / 60;
        int remain = minutes % 60;
        if (hours == 0) {
            return remain + "分钟";
        }
        if (remain == 0) {
            return hours + "小时";
        }
        return hours + "小时" + remain + "分钟";
    }

    static String courseLink(String link, String bvid, Integer source) {
        if (link != null && !link.isBlank()) {
            return link.trim();
        }
        if (source != null && source == 1 && bvid != null && !bvid.isBlank()) {
            return "https://www.bilibili.com/video/" + bvid.trim();
        }
        return null;
    }

    static int progress(int completed, int total) {
        if (total <= 0 || completed <= 0) {
            return 0;
        }
        if (completed >= total) {
            return 100;
        }
        return (int) Math.round(completed * 100.0 / total);
    }
}
