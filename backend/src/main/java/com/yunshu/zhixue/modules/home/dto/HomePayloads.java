package com.yunshu.zhixue.modules.home.dto;

import java.util.List;

public final class HomePayloads {

    private HomePayloads() {
    }

    public record PlatformStats(long studentCount, long questionCount, long interviewCount, int goodRate) {
    }

    public record HotStageView(
            Long stageId,
            Long subjectId,
            String subjectName,
            String stageName,
            String icon,
            long questionCount
    ) {
    }

    public record LearningStats(int consecutiveDays, int accuracyRate, int totalPracticeCount) {
    }

    public record TrendPoint(String date, int count) {
    }

    public record LearningOverview(
            LearningStats learningStats,
            List<TrendPoint> weekTrend,
            int todayCount,
            boolean guideFinished
    ) {
    }
}
