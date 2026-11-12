package com.yunshu.zhixue.modules.interviewbank.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.TopicOption;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.TopicSaveRequest;
import com.yunshu.zhixue.modules.interviewbank.service.InterviewAdminService;
import com.yunshu.zhixue.security.RequirePermission;
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
@RequestMapping("/api/admin/v1/interview-topics")
public class AdminInterviewTopicController {

    private final InterviewAdminService interviewAdminService;

    public AdminInterviewTopicController(InterviewAdminService interviewAdminService) {
        this.interviewAdminService = interviewAdminService;
    }

    @GetMapping
    @RequirePermission("interviewbank:view")
    public ApiResponse<List<TopicOption>> list(@RequestParam(required = false) Long subjectId) {
        return ApiResponse.ok(interviewAdminService.topics(subjectId));
    }

    @PostMapping
    @RequirePermission("interviewbank:edit")
    public ApiResponse<Void> create(@Valid @RequestBody TopicSaveRequest request) {
        interviewAdminService.createTopic(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @RequirePermission("interviewbank:edit")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody TopicSaveRequest request) {
        interviewAdminService.updateTopic(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("interviewbank:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interviewAdminService.deleteTopic(id);
        return ApiResponse.ok();
    }
}
