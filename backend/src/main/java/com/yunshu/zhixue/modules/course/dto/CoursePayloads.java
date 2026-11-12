package com.yunshu.zhixue.modules.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CoursePayloads {

    private CoursePayloads() {
    }

    public record CourseSaveRequest(
            @NotBlank(message = "请输入课程名称") @Size(max = 100, message = "课程名称不超过 100 字") String name,
            @Size(max = 500, message = "封面地址不超过 500 字") String coverUrl,
            @NotNull(message = "请填写时长") @Min(value = 1, message = "时长须大于 0") @Max(value = 600000, message = "时长过长") Integer durationMinutes,
            @NotNull(message = "请选择来源") @Min(value = 1, message = "来源不正确") @Max(value = 2, message = "来源不正确") Integer source,
            @Size(max = 20, message = "BV号不超过 20 字") String bvid,
            @Size(max = 500, message = "课程链接不超过 500 字") String link,
            Long subjectId,
            List<Long> subjectIds
    ) {
    }

    public record ImportConfirmRequest(@NotBlank(message = "缺少导入凭证") String token) {
    }

    public record SubjectOption(Long id, String name) {
    }

    public record CourseStats(long courseCount, double totalHours, long subjectCount, List<SubjectOption> subjects) {
    }

    public record CourseSubjectView(Long id, String name) {
    }

    public record CourseView(
            Long id,
            String name,
            String coverUrl,
            int durationMinutes,
            String duration,
            int source,
            String bvid,
            String link,
            Long subjectId,
            boolean otherSubject,
            List<CourseSubjectView> subjects
    ) {
    }

    public record ImportRow(
            int row,
            String name,
            String subjects,
            String source,
            String duration,
            String bvid,
            String status,
            List<String> messages
    ) {
    }

    public record ImportPreview(
            String token,
            int total,
            int validCount,
            int warningCount,
            int invalidCount,
            List<ImportRow> rows
    ) {
    }

    public record ImportConfirmResult(int imported) {
    }

    public record CourseDraft(
            String name,
            String coverUrl,
            int durationMinutes,
            int source,
            String bvid,
            String link,
            Long subjectId,
            List<Long> subjectIds
    ) {
    }
}
