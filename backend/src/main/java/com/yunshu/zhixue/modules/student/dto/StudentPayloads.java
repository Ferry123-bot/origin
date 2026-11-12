package com.yunshu.zhixue.modules.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StudentPayloads {

    private StudentPayloads() {
    }

    public record StudentItem(long id, String nickname, String avatar, String mobile, LocalDateTime createdAt,
                              long studyDays, long practiceCount, long interviewCount, int status) {
    }

    public record StudentDetail(long id, String nickname, String avatar, String mobile, String email, int status,
                                LocalDateTime createdAt, LocalDateTime lastLoginAt, long studyDays, long courseCount,
                                long practiceCount, int accuracy, long interviewCount) {
    }

    public record InterviewItem(long id, String subjectName, int difficulty, BigDecimal overallScore,
                                int questionCount, LocalDateTime completedAt) {
    }

    public record StatusRequest(@NotNull(message = "请选择状态") @Min(0) @Max(1) Integer status) {
    }

    public record BatchStatusRequest(
            @NotEmpty(message = "请选择学员") @Size(max = 200) List<Long> ids,
            @NotNull(message = "请选择状态") @Min(0) @Max(1) Integer status) {
    }

    public record BatchIdsRequest(@NotEmpty(message = "请选择学员") @Size(max = 200) List<Long> ids) {
    }
}
