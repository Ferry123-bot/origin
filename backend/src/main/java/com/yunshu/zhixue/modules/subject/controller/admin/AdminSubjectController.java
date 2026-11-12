package com.yunshu.zhixue.modules.subject.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.AdminSubjectView;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.SortRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.StageSaveRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.StageStatusRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.SubjectSaveRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.SubjectStats;
import com.yunshu.zhixue.modules.subject.service.SubjectAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/subjects")
public class AdminSubjectController {

    private final SubjectAdminService subjectAdminService;

    public AdminSubjectController(SubjectAdminService subjectAdminService) {
        this.subjectAdminService = subjectAdminService;
    }

    @GetMapping("/stats")
    @RequirePermission("subject:view")
    public ApiResponse<SubjectStats> stats() {
        return ApiResponse.ok(subjectAdminService.stats());
    }

    @GetMapping
    @RequirePermission("subject:view")
    public ApiResponse<List<AdminSubjectView>> list() {
        return ApiResponse.ok(subjectAdminService.list());
    }

    @PostMapping
    @RequirePermission("subject:add")
    public ApiResponse<Void> create(@Valid @RequestBody SubjectSaveRequest request) {
        subjectAdminService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/sort")
    @RequirePermission("subject:edit")
    public ApiResponse<Void> sort(@Valid @RequestBody SortRequest request) {
        subjectAdminService.sort(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @RequirePermission("subject:edit")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SubjectSaveRequest request) {
        subjectAdminService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("subject:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subjectAdminService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{subjectId}/stages")
    @RequirePermission("subject:add")
    public ApiResponse<Void> createStage(@PathVariable Long subjectId, @Valid @RequestBody StageSaveRequest request) {
        subjectAdminService.createStage(subjectId, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{subjectId}/stages/sort")
    @RequirePermission("subject:edit")
    public ApiResponse<Void> sortStages(@PathVariable Long subjectId, @Valid @RequestBody SortRequest request) {
        subjectAdminService.sortStages(subjectId, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{subjectId}/stages/{id}")
    @RequirePermission("subject:edit")
    public ApiResponse<Void> updateStage(@PathVariable Long subjectId, @PathVariable Long id,
                                          @Valid @RequestBody StageSaveRequest request) {
        subjectAdminService.updateStage(subjectId, id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{subjectId}/stages/{id}/status")
    @RequirePermission("subject:edit")
    public ApiResponse<Void> updateStageStatus(@PathVariable Long subjectId, @PathVariable Long id,
                                                @Valid @RequestBody StageStatusRequest request) {
        subjectAdminService.updateStageStatus(subjectId, id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{subjectId}/stages/{id}")
    @RequirePermission("subject:delete")
    public ApiResponse<Void> deleteStage(@PathVariable Long subjectId, @PathVariable Long id) {
        subjectAdminService.deleteStage(subjectId, id);
        return ApiResponse.ok();
    }
}
