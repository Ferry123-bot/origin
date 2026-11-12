package com.yunshu.zhixue.modules.student.controller.portal;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.AvatarView;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.PasswordChangeRequest;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.ProfileUpdateRequest;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.ProfileView;
import com.yunshu.zhixue.modules.student.service.StudentPortalService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/portal/v1")
public class PortalProfileController {

    private final StudentPortalService studentPortalService;

    public PortalProfileController(StudentPortalService studentPortalService) {
        this.studentPortalService = studentPortalService;
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileView> profile() {
        return ApiResponse.ok(studentPortalService.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<Void> update(@Valid @RequestBody ProfileUpdateRequest request) {
        studentPortalService.update(request);
        return ApiResponse.ok();
    }

    @PutMapping("/profile/password")
    public ApiResponse<Void> password(@Valid @RequestBody PasswordChangeRequest request, HttpServletRequest httpRequest) {
        studentPortalService.changePassword(request, httpRequest);
        return ApiResponse.ok();
    }

    @PostMapping("/profile/guide")
    public ApiResponse<Void> finishGuide() {
        studentPortalService.finishGuide();
        return ApiResponse.ok();
    }

    @PostMapping("/files/avatar")
    public ApiResponse<AvatarView> avatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(studentPortalService.uploadAvatar(file));
    }
}
