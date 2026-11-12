package com.yunshu.zhixue.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yunshu.zhixue.common.constant.AppConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthPayloads {

    private AuthPayloads() {
    }

    public record SmsCodeRequest(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = AppConstants.MOBILE_PATTERN, message = "手机号格式不正确")
            String mobile,
            @NotBlank(message = "请选择验证码场景")
            @Pattern(regexp = "register|login|reset", message = "验证码场景不正确")
            String type
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SmsCodeView(int expireSeconds, String debugCode) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = AppConstants.MOBILE_PATTERN, message = "手机号格式不正确")
            String mobile,
            @NotBlank(message = "请输入验证码")
            @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
            String code,
            @NotBlank(message = "请输入密码")
            @Pattern(regexp = AppConstants.PASSWORD_PATTERN, message = AppConstants.PASSWORD_MESSAGE)
            String password
    ) {
    }

    public record PasswordLoginRequest(
            @NotBlank(message = "请输入手机号或邮箱")
            @Size(max = 100, message = "账号过长")
            String account,
            @NotBlank(message = "请输入密码")
            @Size(max = 100, message = "密码过长")
            String password
    ) {
    }

    public record SmsLoginRequest(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = AppConstants.MOBILE_PATTERN, message = "手机号格式不正确")
            String mobile,
            @NotBlank(message = "请输入验证码")
            @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
            String code
    ) {
    }

    public record ResetVerifyRequest(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = AppConstants.MOBILE_PATTERN, message = "手机号格式不正确")
            String mobile,
            @NotBlank(message = "请输入验证码")
            @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
            String code
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "请先完成手机验证")
            String resetToken,
            @NotBlank(message = "请输入新密码")
            @Pattern(regexp = AppConstants.PASSWORD_PATTERN, message = AppConstants.PASSWORD_MESSAGE)
            String newPassword
    ) {
    }

    public record ResetTokenView(String resetToken) {
    }

    public record PortalTokenView(String accessToken, String refreshToken, long expiresIn, String nickname) {
    }

    public record AdminLoginRequest(
            @NotBlank(message = "请输入账号")
            @Size(max = 50, message = "账号过长")
            String account,
            @NotBlank(message = "请输入密码")
            @Size(max = 100, message = "密码过长")
            String password
    ) {
    }

    public record AdminTokenView(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String username,
            String roleCode,
            List<String> permissions
    ) {
    }
}
