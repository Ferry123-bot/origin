package com.yunshu.zhixue.modules.system.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.PermissionNode;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.PermissionSaveRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.RoleCard;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.RoleCreateRequest;
import com.yunshu.zhixue.modules.system.service.RoleAdminService;
import com.yunshu.zhixue.security.RequireSuperAdmin;
import jakarta.validation.Valid;
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

@RestController
public class AdminRoleController {

    private final RoleAdminService roleAdminService;

    public AdminRoleController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/api/admin/v1/roles")
    @RequireSuperAdmin
    public ApiResponse<List<RoleCard>> list() {
        return ApiResponse.ok(roleAdminService.list());
    }

    @PostMapping("/api/admin/v1/roles")
    @RequireSuperAdmin
    public ApiResponse<Void> create(@Valid @RequestBody RoleCreateRequest request) {
        roleAdminService.create(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/admin/v1/roles/{id}")
    @RequireSuperAdmin
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleAdminService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/admin/v1/permissions/tree")
    @RequireSuperAdmin
    public ApiResponse<List<PermissionNode>> tree(@RequestParam Long roleId) {
        return ApiResponse.ok(roleAdminService.tree(roleId));
    }

    @PutMapping("/api/admin/v1/roles/{id}/permissions")
    @RequireSuperAdmin
    public ApiResponse<Void> savePermissions(@PathVariable Long id, @Valid @RequestBody PermissionSaveRequest request) {
        roleAdminService.savePermissions(id, request);
        return ApiResponse.ok();
    }
}
