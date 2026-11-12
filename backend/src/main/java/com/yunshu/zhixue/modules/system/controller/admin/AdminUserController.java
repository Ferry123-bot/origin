package com.yunshu.zhixue.modules.system.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.BatchIdsRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.BatchStatusRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.ResetPasswordView;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.RoleOption;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserCreateRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserDetail;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserListItem;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserUpdateRequest;
import com.yunshu.zhixue.modules.system.service.UserAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/v1/users")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/role-options")
    @RequirePermission("user:view")
    public ApiResponse<List<RoleOption>> roleOptions() {
        return ApiResponse.ok(userAdminService.roleOptions());
    }

    @GetMapping
    @RequirePermission("user:view")
    public ApiResponse<PageResult<UserListItem>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(userAdminService.page(keyword, roleId, status, page, pageSize));
    }

    @PostMapping
    @RequirePermission("user:add")
    public ApiResponse<Void> create(@Valid @RequestBody UserCreateRequest request) {
        userAdminService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/batch-status")
    @RequirePermission("user:edit")
    public ApiResponse<Void> batchStatus(@Valid @RequestBody BatchStatusRequest request) {
        userAdminService.batchStatus(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @RequirePermission("user:edit")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userAdminService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/batch")
    @RequirePermission("user:disable")
    public ApiResponse<Void> batchDelete(@Valid @RequestBody BatchIdsRequest request) {
        userAdminService.batchDelete(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("user:disable")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userAdminService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    @RequirePermission("user:edit")
    public ApiResponse<ResetPasswordView> resetPassword(@PathVariable Long id) {
        return ApiResponse.ok(userAdminService.resetPassword(id));
    }

    @GetMapping("/{id}/detail")
    @RequirePermission("user:view")
    public ApiResponse<UserDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(userAdminService.detail(id));
    }
}
