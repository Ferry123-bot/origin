package com.yunshu.zhixue.modules.study.controller.portal;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeStartResult;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.RedoStartRequest;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.WrongQuestionItem;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.WrongStats;
import com.yunshu.zhixue.modules.study.service.WrongBookPortalService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/portal/v1/wrong-questions")
public class PortalWrongBookController {

    private final WrongBookPortalService wrongBookPortalService;

    public PortalWrongBookController(WrongBookPortalService wrongBookPortalService) {
        this.wrongBookPortalService = wrongBookPortalService;
    }

    @GetMapping("/stats")
    public ApiResponse<WrongStats> stats() {
        return ApiResponse.ok(wrongBookPortalService.stats());
    }

    @GetMapping
    public ApiResponse<PageResult<WrongQuestionItem>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(wrongBookPortalService.page(status, type, days, sort, page, pageSize));
    }

    @PostMapping("/redo-start")
    public ApiResponse<PracticeStartResult> redo(@RequestBody RedoStartRequest request) {
        return ApiResponse.ok(wrongBookPortalService.redo(request));
    }

    @PostMapping("/{id}/mastered")
    public ApiResponse<Void> mastered(@PathVariable long id) {
        wrongBookPortalService.mastered(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable long id) {
        wrongBookPortalService.remove(id);
        return ApiResponse.ok();
    }
}
