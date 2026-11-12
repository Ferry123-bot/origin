package com.yunshu.zhixue.modules.auth.controller.portal;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.util.ClientIp;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.PasswordLoginRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.PortalTokenView;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.RegisterRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.ResetPasswordRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.ResetTokenView;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.ResetVerifyRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.SmsCodeRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.SmsCodeView;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.SmsLoginRequest;
import com.yunshu.zhixue.modules.auth.service.PortalAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal/v1/auth")
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    public PortalAuthController(PortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
    }

    @PostMapping("/sms-code")
    public ApiResponse<SmsCodeView> smsCode(@Valid @RequestBody SmsCodeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(portalAuthService.sendSms(request, ClientIp.resolve(http)));
    }

    @PostMapping("/register")
    public ApiResponse<PortalTokenView> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return ApiResponse.ok(portalAuthService.register(request, ClientIp.resolve(http)));
    }

    @PostMapping("/login/password")
    public ApiResponse<PortalTokenView> loginByPassword(@Valid @RequestBody PasswordLoginRequest request, HttpServletRequest http) {
        return ApiResponse.ok(portalAuthService.loginByPassword(request, ClientIp.resolve(http)));
    }

    @PostMapping("/login/sms")
    public ApiResponse<PortalTokenView> loginBySms(@Valid @RequestBody SmsLoginRequest request, HttpServletRequest http) {
        return ApiResponse.ok(portalAuthService.loginBySms(request, ClientIp.resolve(http)));
    }

    @PostMapping("/reset/verify")
    public ApiResponse<ResetTokenView> verifyReset(@Valid @RequestBody ResetVerifyRequest request, HttpServletRequest http) {
        return ApiResponse.ok(portalAuthService.verifyReset(request, ClientIp.resolve(http)));
    }

    @PostMapping("/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        portalAuthService.resetPassword(request, ClientIp.resolve(http));
        return ApiResponse.ok();
    }

    @PostMapping("/refresh")
    public ApiResponse<PortalTokenView> refresh(@RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        return ApiResponse.ok(portalAuthService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        portalAuthService.logout(authorization, refreshToken);
        return ApiResponse.ok();
    }
}
