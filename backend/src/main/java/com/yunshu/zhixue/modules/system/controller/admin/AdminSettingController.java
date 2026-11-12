package com.yunshu.zhixue.modules.system.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.ImageView;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.SettingsSaveRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.SettingsView;
import com.yunshu.zhixue.modules.system.service.ImageStorageService;
import com.yunshu.zhixue.modules.system.service.SettingAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AdminSettingController {

    private final SettingAdminService settingAdminService;
    private final ImageStorageService imageStorageService;

    public AdminSettingController(SettingAdminService settingAdminService, ImageStorageService imageStorageService) {
        this.settingAdminService = settingAdminService;
        this.imageStorageService = imageStorageService;
    }

    @GetMapping("/api/admin/v1/settings")
    public ApiResponse<SettingsView> get() {
        return ApiResponse.ok(settingAdminService.get());
    }

    @PutMapping("/api/admin/v1/settings")
    @RequirePermission("system:setting")
    public ApiResponse<Void> save(@Valid @RequestBody SettingsSaveRequest request) {
        settingAdminService.save(request);
        return ApiResponse.ok();
    }

    @PostMapping("/api/admin/v1/settings/reset")
    @RequirePermission("system:setting")
    public ApiResponse<Void> reset() {
        settingAdminService.reset();
        return ApiResponse.ok();
    }

    @PostMapping("/api/admin/v1/files/image")
    @RequirePermission("system:setting")
    public ApiResponse<ImageView> upload(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "avatar") String kind) {
        return ApiResponse.ok(imageStorageService.upload(file, kind));
    }
}
