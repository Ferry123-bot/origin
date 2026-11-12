package com.yunshu.zhixue.modules.student.dto;

import com.yunshu.zhixue.common.constant.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProfilePayloads {

    private ProfilePayloads() {
    }

    public record ProfileStats(int practiceCount, int interviewCount, int wrongQuestionCount) {
    }

    public record StudyStats(int totalDays, int accuracyRate) {
    }

    public record ProfileView(
            Long id,
            String nickname,
            String mobile,
            String email,
            Integer gender,
            Long preferredSubjectId,
            String avatar,
            boolean guideFinished,
            ProfileStats stats,
            StudyStats study
    ) {
    }

    public record ProfileUpdateRequest(
            @NotBlank(message = "请输入用户名")
            @Size(max = 20, message = "用户名不超过 20 个字符")
            String nickname,
            @Size(max = 100, message = "邮箱过长")
            @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "邮箱格式不正确")
            String email,
            @NotNull(message = "请选择性别")
            @Min(value = 0, message = "性别不正确")
            @Max(value = 2, message = "性别不正确")
            Integer gender,
            @Size(max = 500, message = "头像地址过长")
            String avatar
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank(message = "请输入原密码")
            String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Pattern(regexp = AppConstants.PASSWORD_PATTERN, message = AppConstants.PASSWORD_MESSAGE)
            String newPassword
    ) {
    }

    public record AvatarView(String url) {
    }
}
