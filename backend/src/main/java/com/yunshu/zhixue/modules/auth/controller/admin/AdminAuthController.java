package com.yunshu.zhixue.modules.auth.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.util.ClientIp;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.AdminLoginRequest;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.AdminTokenView;
import com.yunshu.zhixue.modules.auth.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminTokenView> login(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest http) {
        return ApiResponse.ok(adminAuthService.login(request, ClientIp.resolve(http)));
    }

    @PostMapping("/refresh")
    public ApiResponse<AdminTokenView> refresh(@RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        return ApiResponse.ok(adminAuthService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken,
                                    HttpServletRequest http) {
        adminAuthService.logout(authorization, refreshToken, ClientIp.resolve(http));
        return ApiResponse.ok();
    }
}
