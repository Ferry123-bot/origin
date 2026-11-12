package com.yunshu.zhixue.modules.course.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseSaveRequest;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseStats;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseView;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.ImportConfirmRequest;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.ImportConfirmResult;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.ImportPreview;
import com.yunshu.zhixue.modules.course.service.CourseAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/admin/v1/courses")
public class AdminCourseController {

    private final CourseAdminService courseAdminService;

    public AdminCourseController(CourseAdminService courseAdminService) {
        this.courseAdminService = courseAdminService;
    }

    @GetMapping("/stats")
    @RequirePermission("course:view")
    public ApiResponse<CourseStats> stats() {
        return ApiResponse.ok(courseAdminService.stats());
    }

    @GetMapping("/import/template")
    @RequirePermission("course:add")
    public ResponseEntity<byte[]> template() {
        String filename = URLEncoder.encode("课程导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(courseAdminService.template());
    }

    @GetMapping
    @RequirePermission("course:view")
    public ApiResponse<PageResult<CourseView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Integer source,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(courseAdminService.page(keyword, subjectId, source, page, pageSize));
    }

    @PostMapping
    @RequirePermission("course:add")
    public ApiResponse<Void> create(@Valid @RequestBody CourseSaveRequest request) {
        courseAdminService.create(request);
        return ApiResponse.ok();
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("course:add")
    public ApiResponse<ImportPreview> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(courseAdminService.preview(file));
    }

    @PostMapping("/import/confirm")
    @RequirePermission("course:add")
    public ApiResponse<ImportConfirmResult> confirm(@Valid @RequestBody ImportConfirmRequest request) {
        return ApiResponse.ok(courseAdminService.confirm(request.token()));
    }

    @PutMapping("/{id}")
    @RequirePermission("course:edit")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CourseSaveRequest request) {
        courseAdminService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("course:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseAdminService.delete(id);
        return ApiResponse.ok();
    }
}
