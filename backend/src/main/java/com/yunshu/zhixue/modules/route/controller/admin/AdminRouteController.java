package com.yunshu.zhixue.modules.route.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.AddCoursesRequest;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.AddCoursesResult;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.CourseIdsRequest;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.CourseOption;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteBoard;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteDetail;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteSaveRequest;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteStatusRequest;
import com.yunshu.zhixue.modules.route.service.RouteAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/admin/v1/routes")
public class AdminRouteController {

    private final RouteAdminService routeAdminService;

    public AdminRouteController(RouteAdminService routeAdminService) {
        this.routeAdminService = routeAdminService;
    }

    @GetMapping
    @RequirePermission("route:view")
    public ApiResponse<RouteBoard> board() {
        return ApiResponse.ok(routeAdminService.board());
    }

    @GetMapping("/course-options")
    @RequirePermission("route:view")
    public ApiResponse<List<CourseOption>> courseOptions(@RequestParam @NotNull(message = "请选择关联学科") Long subjectId) {
        return ApiResponse.ok(routeAdminService.courseOptions(subjectId));
    }

    @GetMapping("/{id}/detail")
    @RequirePermission("route:view")
    public ApiResponse<RouteDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(routeAdminService.detail(id));
    }

    @PostMapping
    @RequirePermission("route:add")
    public ApiResponse<Void> create(@Valid @RequestBody RouteSaveRequest request) {
        routeAdminService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @RequirePermission("route:edit")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody RouteSaveRequest request) {
        routeAdminService.update(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/status")
    @RequirePermission("route:edit")
    public ApiResponse<Void> status(@PathVariable Long id, @Valid @RequestBody RouteStatusRequest request) {
        routeAdminService.changeStatus(id, request.status());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("route:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        routeAdminService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/courses")
    @RequirePermission("route:edit")
    public ApiResponse<AddCoursesResult> addCourses(@PathVariable Long id, @Valid @RequestBody AddCoursesRequest request) {
        return ApiResponse.ok(routeAdminService.addCourses(id, request.courseIds()));
    }

    @DeleteMapping("/{id}/courses/{courseId}")
    @RequirePermission("route:edit")
    public ApiResponse<Void> removeCourse(@PathVariable Long id, @PathVariable Long courseId) {
        routeAdminService.removeCourse(id, courseId);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/courses/sort")
    @RequirePermission("route:edit")
    public ApiResponse<Void> sort(@PathVariable Long id, @Valid @RequestBody CourseIdsRequest request) {
        routeAdminService.sort(id, request.courseIds());
        return ApiResponse.ok();
    }
}
