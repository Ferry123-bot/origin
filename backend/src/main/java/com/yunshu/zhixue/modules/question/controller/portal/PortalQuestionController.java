package com.yunshu.zhixue.modules.question.controller.portal;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.question.dto.PortalQuestionPayloads.PortalQuestionDetail;
import com.yunshu.zhixue.modules.question.dto.PortalQuestionPayloads.PortalQuestionItem;
import com.yunshu.zhixue.modules.question.service.QuestionPortalService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/portal/v1/questions")
public class PortalQuestionController {

    private final QuestionPortalService questionPortalService;

    public PortalQuestionController(QuestionPortalService questionPortalService) {
        this.questionPortalService = questionPortalService;
    }

    @GetMapping
    public ApiResponse<PageResult<PortalQuestionItem>> page(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(questionPortalService.page(subjectId, stageId, type, difficulty, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<PortalQuestionDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(questionPortalService.detail(id));
    }
}
