package com.yunshu.zhixue.modules.interviewbank.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.ImportConfirmRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.ImportConfirmResult;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.ImportPreview;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionCreateRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionDetail;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionItem;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionUpdateRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.SearchHit;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.SearchRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.SubjectOption;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.UploadAccepted;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.UploadStatus;
import com.yunshu.zhixue.modules.interviewbank.service.InterviewAdminService;
import com.yunshu.zhixue.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/v1/interview-questions")
public class AdminInterviewQuestionController {

    private final InterviewAdminService interviewAdminService;

    public AdminInterviewQuestionController(InterviewAdminService interviewAdminService) {
        this.interviewAdminService = interviewAdminService;
    }

    @GetMapping("/subjects")
    @RequirePermission("interviewbank:view")
    public ApiResponse<List<SubjectOption>> subjects() {
        return ApiResponse.ok(interviewAdminService.subjects());
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("interviewbank:edit")
    public ApiResponse<ImportPreview> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(interviewAdminService.preview(file));
    }

    @PostMapping("/import/confirm")
    @RequirePermission("interviewbank:edit")
    public ApiResponse<ImportConfirmResult> confirm(@Valid @RequestBody ImportConfirmRequest request) {
        return ApiResponse.ok(interviewAdminService.confirm(request));
    }

    @PostMapping("/search-test")
    @RequirePermission("interviewbank:search_test")
    public ApiResponse<List<SearchHit>> search(@Valid @RequestBody SearchRequest request) {
        return ApiResponse.ok(interviewAdminService.search(request.keyword()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("interviewbank:upload")
    public ApiResponse<UploadAccepted> upload(@RequestParam("file") MultipartFile file, @RequestParam Long subjectId) {
        return ApiResponse.ok(interviewAdminService.startUpload(file, subjectId));
    }

    @GetMapping("/upload/{taskId}")
    @RequirePermission("interviewbank:upload")
    public ApiResponse<UploadStatus> uploadStatus(@PathVariable String taskId) {
        return ApiResponse.ok(interviewAdminService.uploadStatus(taskId));
    }

    @GetMapping
    @RequirePermission("interviewbank:view")
    public ApiResponse<PageResult<QuestionItem>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Long topicSubjectId,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Boolean unclassified,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize) {
        return ApiResponse.ok(interviewAdminService.page(keyword, subjectId, topicId, topic, topicSubjectId, difficulty, unclassified, page, pageSize));
    }

    @PostMapping
    @RequirePermission("interviewbank:edit")
    public ApiResponse<Void> create(@Valid @RequestBody QuestionCreateRequest request) {
        interviewAdminService.create(request);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}")
    @RequirePermission("interviewbank:view")
    public ApiResponse<QuestionDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(interviewAdminService.detail(id));
    }

    @PutMapping("/{id}")
    @RequirePermission("interviewbank:edit")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionUpdateRequest request) {
        interviewAdminService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("interviewbank:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interviewAdminService.delete(id);
        return ApiResponse.ok();
    }
}
