package com.yunshu.zhixue.modules.system.dto;

import com.yunshu.zhixue.common.constant.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemPayloads {

    private SystemPayloads() {
    }

    public record UserCreateRequest(
            @NotBlank(message = "请输入用户名") @Size(max = 50) @Pattern(regexp = "\\S{1,50}", message = "用户名不能包含空格") String username,
            @NotBlank(message = "请输入手机号") @Pattern(regexp = AppConstants.MOBILE_PATTERN, message = "手机号格式不正确") String mobile,
            @Size(max = 100) String email,
            @NotNull(message = "请选择角色") Long roleId,
            @NotBlank(message = "请输入密码") @Pattern(regexp = AppConstants.PASSWORD_PATTERN, message = AppConstants.PASSWORD_MESSAGE) String password,
            @NotNull(message = "请选择状态") @Min(0) @Max(1) Integer status
    ) {
    }

    public record UserUpdateRequest(
            @NotBlank(message = "请输入用户名") @Size(max = 50) @Pattern(regexp = "\\S{1,50}", message = "用户名不能包含空格") String username,
            @NotBlank(message = "请输入手机号") @Pattern(regexp = AppConstants.MOBILE_PATTERN, message = "手机号格式不正确") String mobile,
            @Size(max = 100) String email,
            @NotNull(message = "请选择角色") Long roleId,
            @Pattern(regexp = "^$|^\\S{6,20}$", message = AppConstants.PASSWORD_MESSAGE) String password,
            @NotNull(message = "请选择状态") @Min(0) @Max(1) Integer status
    ) {
    }

    public record BatchStatusRequest(
            @NotEmpty(message = "请选择用户") List<Long> ids,
            @NotNull(message = "请选择状态") @Min(0) @Max(1) Integer status
    ) {
    }

    public record BatchIdsRequest(@NotEmpty(message = "请选择用户") List<Long> ids) {
    }

    public record RoleOption(Long id, String name, String code) {
    }

    public record UserListItem(
            Long id,
            String username,
            String mobile,
            String email,
            Long roleId,
            String roleName,
            int status,
            boolean builtin,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
    }

    public record LogBrief(Long id, String action, String targetType, String targetName, LocalDateTime createdAt) {
    }

    public record UserDetail(
            Long id,
            String username,
            String mobile,
            String email,
            Long roleId,
            String roleName,
            int status,
            boolean builtin,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            List<LogBrief> recentLogs
    ) {
    }

    public record ResetPasswordView(String password) {
    }

    public record RoleCreateRequest(
            @NotBlank(message = "请输入角色名称") @Size(max = 50) String name,
            @NotBlank(message = "请输入角色编码") @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}$", message = "编码需为小写字母、数字或下划线") String code,
            @Size(max = 255) String description
    ) {
    }

    public record RoleCard(
            Long id,
            String name,
            String code,
            String description,
            long userCount,
            List<String> permissionTags,
            boolean protectedRole
    ) {
    }

    public record PermissionNode(Long id, String name, String code, boolean checked, List<PermissionNode> children) {
    }

    public record PermissionSaveRequest(@NotNull(message = "请提交权限") List<Long> permissionIds) {
    }

    public record LogItem(
            Long id,
            String username,
            String action,
            String targetType,
            String targetName,
            String ip,
            LocalDateTime createdAt
    ) {
    }

    public record SettingsView(String siteName, String siteDescription, String logoUrl, String defaultAvatar) {
    }

    public record SettingsSaveRequest(
            @NotBlank(message = "请输入平台名称") @Size(max = 50, message = "平台名称不超过 50 字") String siteName,
            @Size(max = 500, message = "平台描述不超过 500 字") String siteDescription,
            @Size(max = 500) String logoUrl,
            @Size(max = 500) String defaultAvatar
    ) {
    }

    public record ImageView(String url) {
    }
}
