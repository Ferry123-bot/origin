package com.yunshu.zhixue.modules.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SubjectPayloads {

    private SubjectPayloads() {
    }

    public record SubjectSaveRequest(
            @NotBlank(message = "请输入学科名称") @Size(max = 50, message = "学科名称不超过 50 字") String name,
            @NotBlank(message = "请填写学科图标") @Size(max = 50, message = "图标过长") String icon,
            @NotBlank(message = "请输入学科描述") @Size(max = 255, message = "描述不超过 255 字") String description,
            @NotNull(message = "请填写排序号") @Min(0) @Max(9999) Integer sortOrder,
            @NotBlank(message = "请选择主题颜色") @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "主题颜色格式不正确") String themeColor
    ) {
    }

    public record StageSaveRequest(
            @Size(max = 50, message = "图标过长") String icon,
            @NotBlank(message = "请输入阶段名称") @Size(max = 50, message = "阶段名称不超过 50 字") String name,
            @NotNull(message = "请填写排序号") @Min(0) @Max(9999) Integer sortOrder
    ) {
    }

    public record SortRequest(
            @NotNull(message = "缺少排序对象") Long id,
            @NotBlank(message = "缺少移动方向") @Pattern(regexp = "up|down", message = "移动方向不正确") String move
    ) {
    }

    public record StageStatusRequest(
            @NotNull(message = "请指定状态") @Min(0) @Max(1) Integer status
    ) {
    }

    public record SubjectStats(long subjectCount, long subjectsWithStages, long stageCount, long questionCount) {
    }

    public record AdminStageView(Long id, String name, String icon, int sortOrder, int status, long questionCount) {
    }

    public record AdminSubjectView(
            Long id,
            String name,
            String icon,
            String description,
            String themeColor,
            int sortOrder,
            long courseCount,
            long routeCount,
            long questionCount,
            List<AdminStageView> stages
    ) {
    }

    public record PortalSubjectView(
            Long id,
            String name,
            String icon,
            String description,
            String themeColor,
            long stageCount,
            int sortOrder
    ) {
    }

    public record PortalStageView(Long id, String name, String icon, long questionCount, int sortOrder) {
    }
}
