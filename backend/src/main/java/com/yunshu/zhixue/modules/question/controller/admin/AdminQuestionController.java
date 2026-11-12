package com.yunshu.zhixue.modules.question.controller.admin;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.AuditRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.BatchIdsRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.BatchResult;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.BatchStatusRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.ImportConfirmRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.ImportConfirmResult;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.ImportPreview;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionDetail;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionItem;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionSaveRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionStats;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.StageOption;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.StatusRequest;
import com.yunshu.zhixue.modules.question.service.QuestionAdminService;
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
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/v1/questions")
public class AdminQuestionController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final QuestionAdminService questionAdminService;

    public AdminQuestionController(QuestionAdminService questionAdminService) {
        this.questionAdminService = questionAdminService;
    }

    @GetMapping("/stats")
    @RequirePermission("question:view")
    public ApiResponse<QuestionStats> stats() {
        return ApiResponse.ok(questionAdminService.stats());
    }

    @GetMapping("/stages")
    @RequirePermission("question:view")
    public ApiResponse<List<StageOption>> stages() {
        return ApiResponse.ok(questionAdminService.stages());
    }

    @GetMapping("/import/template")
    @RequirePermission("question:import")
    public ResponseEntity<byte[]> template() {
        return download("题库导入模板.xlsx", questionAdminService.template());
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("question:import")
    public ApiResponse<ImportPreview> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(questionAdminService.preview(file));
    }

    @PostMapping("/import/confirm")
    @RequirePermission("question:import")
    public ApiResponse<ImportConfirmResult> confirm(@Valid @RequestBody ImportConfirmRequest request) {
        return ApiResponse.ok(questionAdminService.confirm(request.token()));
    }

    @GetMapping("/export")
    @RequirePermission("question:export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer status) {
        return download("题库导出.xlsx", questionAdminService.export(keyword, subjectId, stageId, type, difficulty, status));
    }

    @GetMapping
    @RequirePermission("question:view")
    public ApiResponse<PageResult<QuestionItem>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(questionAdminService.page(keyword, subjectId, stageId, type, difficulty, status, page, pageSize));
    }

    @PostMapping
    @RequirePermission("question:add")
    public ApiResponse<Void> create(@Valid @RequestBody QuestionSaveRequest request) {
        questionAdminService.create(request);
        return ApiResponse.ok();
    }

    @PostMapping("/batch-status")
    @RequirePermission("question:edit")
    public ApiResponse<BatchResult> batchStatus(@Valid @RequestBody BatchStatusRequest request) {
        return ApiResponse.ok(questionAdminService.batchStatus(request));
    }

    @PostMapping("/batch-delete")
    @RequirePermission("question:delete")
    public ApiResponse<BatchResult> batchDelete(@Valid @RequestBody BatchIdsRequest request) {
        return ApiResponse.ok(questionAdminService.batchDelete(request));
    }

    @PostMapping("/batch-pass")
    @RequirePermission("question:audit")
    public ApiResponse<BatchResult> batchPass(@Valid @RequestBody BatchIdsRequest request) {
        return ApiResponse.ok(questionAdminService.batchPass(request));
    }

    @GetMapping("/{id}")
    @RequirePermission("question:view")
    public ApiResponse<QuestionDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(questionAdminService.detail(id));
    }

    @PutMapping("/{id}")
    @RequirePermission("question:edit")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody QuestionSaveRequest request) {
        questionAdminService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("question:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionAdminService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/audit")
    @RequirePermission("question:audit")
    public ApiResponse<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditRequest request) {
        questionAdminService.audit(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/status")
    @RequirePermission("question:edit")
    public ApiResponse<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        questionAdminService.changeStatus(id, request.action());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/resubmit")
    @RequirePermission("question:edit")
    public ApiResponse<Void> resubmit(@PathVariable Long id) {
        questionAdminService.resubmit(id);
        return ApiResponse.ok();
    }

    private ResponseEntity<byte[]> download(String filename, byte[] body) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(XLSX)
                .body(body);
    }
}
