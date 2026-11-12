package com.yunshu.zhixue.modules.student.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.BatchIdsRequest;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.BatchStatusRequest;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.InterviewItem;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.StatusRequest;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.StudentDetail;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.StudentItem;
import com.yunshu.zhixue.modules.student.service.StudentAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/v1/students")
public class AdminStudentController {

    private final StudentAdminService studentAdminService;

    public AdminStudentController(StudentAdminService studentAdminService) {
        this.studentAdminService = studentAdminService;
    }

    @GetMapping("/export")
    @RequirePermission("student:view")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return studentAdminService.export(keyword, status);
    }

    @GetMapping
    @RequirePermission("student:view")
    public ApiResponse<PageResult<StudentItem>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(studentAdminService.page(keyword, status, page, pageSize));
    }

    @PutMapping("/batch-status")
    @RequirePermission("student:edit")
    public ApiResponse<Void> batchStatus(@Valid @RequestBody BatchStatusRequest request) {
        studentAdminService.batchStatus(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/batch")
    @RequirePermission("student:delete")
    public ApiResponse<Void> batchDelete(@Valid @RequestBody BatchIdsRequest request) {
        studentAdminService.batchDelete(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("student:edit")
    public ApiResponse<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        studentAdminService.changeStatus(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("student:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        studentAdminService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/detail")
    @RequirePermission("student:view")
    public ApiResponse<StudentDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(studentAdminService.detail(id));
    }

    @GetMapping("/{id}/interviews")
    @RequirePermission("student:view")
    public ApiResponse<PageResult<InterviewItem>> interviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize) {
        return ApiResponse.ok(studentAdminService.interviews(id, page, pageSize));
    }
}
