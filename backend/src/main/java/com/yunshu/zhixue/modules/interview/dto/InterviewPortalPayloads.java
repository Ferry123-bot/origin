package com.yunshu.zhixue.modules.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class InterviewPortalPayloads {

    private InterviewPortalPayloads() {
    }

    public record SubjectOption(long subjectId, String subjectName) {
    }

    public record ConfigView(List<SubjectOption> subjects, List<Integer> difficulties, List<Integer> questionCountOptions,
                             int defaultCount, int remainingCount, int timeoutMinutes) {
    }

    public record StartRequest(Long subjectId, Integer difficulty, Integer count) {
    }

    public record QuestionView(int order, long subjectId, int difficulty, String content, String source) {
    }

    public record StartResult(String sessionId, List<QuestionView> questions, int questionCount) {
    }

    public record AnswerRequest(Integer order, String studentAnswer, Integer durationSeconds) {
    }

    public record PreparedGrade(int order, int score, String feedback, boolean degraded, List<String> referencePoints) {
    }

    public record FinishAnswer(Integer order, String studentAnswer, Integer durationSeconds) {
    }

    public record FinishRequest(List<FinishAnswer> answers) {
    }

    public record FinishResult(long reportId, BigDecimal overallScore) {
    }

    public record Dimension(String name, int score) {
    }

    public record Weakness(String text, String advice) {
    }

    public record ImproveCard(String title, String content) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QuestionReview(int order, String questionText, String studentAnswer, BigDecimal score, String feedback) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReportView(long reportId, long recordId, long subjectId, String subjectName, int difficulty,
                             int questionCount, int totalDuration, LocalDateTime completedAt, BigDecimal overallScore,
                             int accuracyRate, BigDecimal trendDelta, BigDecimal aiCoverage, List<Dimension> dimensions,
                             List<String> strengths, List<Weakness> weaknesses, List<String> suggestions,
                             List<ImproveCard> improveCards, List<QuestionReview> questionReviews) {
    }

    public record RecordItem(long recordId, long subjectId, String subjectName, int difficulty, int questionCount,
                             BigDecimal overallScore, int totalDuration, LocalDateTime completedAt, boolean hasReport) {
    }

    public record InterviewStats(long interviewTotal, BigDecimal avgScore, BigDecimal maxScore, long totalDuration) {
    }
}
