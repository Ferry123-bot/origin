package com.yunshu.zhixue.modules.system.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.LogItem;
import com.yunshu.zhixue.modules.system.service.LogAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/admin/v1/logs")
public class AdminLogController {

    private final LogAdminService logAdminService;

    public AdminLogController(LogAdminService logAdminService) {
        this.logAdminService = logAdminService;
    }

    @GetMapping
    @RequirePermission("system:log_view")
    public ApiResponse<PageResult<LogItem>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(logAdminService.page(keyword, operationType, startDate, endDate, page, pageSize));
    }

    @GetMapping("/export")
    @RequirePermission("system:log_view")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return logAdminService.export(keyword, operationType, startDate, endDate);
    }

    @GetMapping("/{id}")
    @RequirePermission("system:log_view")
    public ApiResponse<LogItem> detail(@PathVariable Long id) {
        return ApiResponse.ok(logAdminService.detail(id));
    }

    @DeleteMapping
    @RequirePermission("system:log_view")
    public ApiResponse<Void> clear() {
        logAdminService.clear();
        return ApiResponse.ok();
    }
}
