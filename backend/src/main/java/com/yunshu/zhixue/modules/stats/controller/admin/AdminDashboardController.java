package com.yunshu.zhixue.modules.stats.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.ActivityItem;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.Overview;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.QuestionDistribution;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.UserGrowth;
import com.yunshu.zhixue.modules.stats.service.DashboardService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/v1/dashboard")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    @RequirePermission("dashboard:view")
    public ApiResponse<Overview> overview() {
        return ApiResponse.ok(dashboardService.overview());
    }

    @GetMapping("/user-growth")
    @RequirePermission("dashboard:view")
    public ApiResponse<UserGrowth> userGrowth(@RequestParam(defaultValue = "7") int range) {
        return ApiResponse.ok(dashboardService.userGrowth(range));
    }

    @GetMapping("/question-distribution")
    @RequirePermission("dashboard:view")
    public ApiResponse<QuestionDistribution> questionDistribution() {
        return ApiResponse.ok(dashboardService.questionDistribution());
    }

    @GetMapping("/recent-activities")
    @RequirePermission("dashboard:view")
    public ApiResponse<PageResult<ActivityItem>> recentActivities(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(dashboardService.recentActivities(page, pageSize));
    }
}
