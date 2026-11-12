package com.yunshu.zhixue.modules.stats.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class DashboardPayloads {

    private DashboardPayloads() {
    }

    /** 环比按上海自然日：今日新增相对昨日新增。昨日为 0 时 changeRate 为空。 */
    public record Metric(long total, long today, long yesterday, Double changeRate) {
    }

    public record Overview(Metric subjects, Metric questions, Metric students, Metric interviews) {
    }

    public record GrowthPoint(String date, long count) {
    }

    public record UserGrowth(int range, List<GrowthPoint> points) {
    }

    public record SubjectSlice(Long subjectId, String name, String themeColor, long count) {
    }

    public record QuestionDistribution(List<SubjectSlice> items) {
    }

    public record ActivityItem(long id, String username, String action, String detail, LocalDateTime createdAt) {
    }
}
