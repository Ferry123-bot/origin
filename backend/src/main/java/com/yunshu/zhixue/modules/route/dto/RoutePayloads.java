package com.yunshu.zhixue.modules.route.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class RoutePayloads {

    private RoutePayloads() {
    }

    public record RouteSaveRequest(
            @NotBlank(message = "请填写路线名称") @Size(max = 100, message = "路线名称不能超过 100 字") String name,
            @NotNull(message = "请选择关联学科") Long subjectId,
            @Size(max = 100, message = "适合学历不能超过 100 字") String targetEducation,
            @Size(max = 50, message = "路线图标不能超过 50 个字符") String icon,
            @Size(max = 5000, message = "路线描述不能超过 5000 字") String description,
            @Min(value = 0, message = "状态不正确") @Max(value = 1, message = "状态不正确") Integer status,
            List<@NotNull(message = "课程不能为空") Long> courseIds
    ) {
    }

    public record RouteStatusRequest(
            @NotNull(message = "请指定状态") @Min(value = 0, message = "状态不正确") @Max(value = 1, message = "状态不正确") Integer status
    ) {
    }

    public record CourseIdsRequest(
            @NotNull(message = "请提交课程顺序") List<@NotNull(message = "课程不能为空") Long> courseIds
    ) {
    }

    public record AddCoursesRequest(
            @NotEmpty(message = "请选择要添加的课程") List<@NotNull(message = "课程不能为空") Long> courseIds
    ) {
    }

    public record CompleteRequest(@NotNull(message = "请指定课程") Long courseId) {
    }

    public record SubjectOption(Long id, String name, String icon, String themeColor) {
    }

    public record CoursePreview(Long id, String name, String coverUrl, String bvid, int durationMinutes) {
    }

    public record RouteCard(
            Long id,
            String name,
            Integer status,
            Long subjectId,
            String subjectName,
            String subjectIcon,
            String icon,
            String targetEducation,
            String description,
            int courseCount,
            int totalMinutes,
            String totalDuration,
            List<CoursePreview> previews
    ) {
    }

    public record RouteBoard(List<SubjectOption> subjects, List<RouteCard> routes) {
    }

    public record RouteCourseItem(
            Long routeCourseId,
            Long courseId,
            String name,
            String bvid,
            int durationMinutes,
            String duration,
            String coverUrl,
            String link,
            Integer source,
            Long subjectId,
            String subjectName,
            boolean crossSubject,
            int sortOrder
    ) {
    }

    public record RouteDetail(
            Long id,
            String name,
            Long subjectId,
            String subjectName,
            String subjectIcon,
            Integer status,
            String icon,
            String targetEducation,
            String description,
            int courseCount,
            int totalMinutes,
            String totalDuration,
            List<RouteCourseItem> courses,
            List<RouteCourseItem> ownCourses,
            List<RouteCourseItem> otherCourses
    ) {
    }

    public record CourseOption(
            Long id,
            String name,
            String bvid,
            int durationMinutes,
            String duration,
            String coverUrl,
            Long subjectId,
            String subjectName,
            boolean own
    ) {
    }

    public record AddCoursesResult(int added) {
    }

    public record PortalRouteView(
            Long id,
            String name,
            Long subjectId,
            String subjectName,
            String targetEducation,
            String description,
            int courseCount,
            int completedCourseCount,
            int progress
    ) {
    }

    public record PortalCourseItem(
            Long routeCourseId,
            Long courseId,
            String name,
            String bvid,
            int durationMinutes,
            Integer source,
            String coverUrl,
            String link,
            boolean completed,
            int sortOrder,
            boolean crossSubject
    ) {
    }

    public record PortalRouteDetail(
            Long id,
            String name,
            Long subjectId,
            String subjectName,
            String icon,
            String targetEducation,
            String description,
            int courseCount,
            int totalMinutes,
            int completedCourseCount,
            int progress,
            List<PortalCourseItem> courses
    ) {
    }

    public record RouteProgress(int completedCourseCount, int courseCount, int progress) {
    }
}
