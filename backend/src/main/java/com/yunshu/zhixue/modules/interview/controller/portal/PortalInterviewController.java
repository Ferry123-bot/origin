package com.yunshu.zhixue.modules.interview.controller.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.AnswerRequest;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.ConfigView;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.FinishRequest;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.FinishResult;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.InterviewStats;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.PreparedGrade;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.RecordItem;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.ReportView;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.StartRequest;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.StartResult;
import com.yunshu.zhixue.modules.interview.service.InterviewPortalService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Validated
@RestController
@RequestMapping("/api/portal/v1/interview")
public class PortalInterviewController {

    private static final long GRADE_STREAM_TIMEOUT_MILLIS = 60_000L;
    private static final int TOKEN_CHUNK_CHARS = 8;

    private final InterviewPortalService interviewPortalService;
    private final ObjectMapper objectMapper;
    private final Executor interviewGradeExecutor;

    public PortalInterviewController(InterviewPortalService interviewPortalService, ObjectMapper objectMapper,
                                     @Qualifier("interviewGradeExecutor") Executor interviewGradeExecutor) {
        this.interviewPortalService = interviewPortalService;
        this.objectMapper = objectMapper;
        this.interviewGradeExecutor = interviewGradeExecutor;
    }

    @GetMapping("/config")
    public ApiResponse<ConfigView> config() {
        return ApiResponse.ok(interviewPortalService.config());
    }

    @PostMapping("/sessions")
    public ApiResponse<StartResult> start(@RequestBody StartRequest request,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(interviewPortalService.start(request, idempotencyKey));
    }

    @PostMapping(value = "/sessions/{id}/answers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@PathVariable String id, @RequestBody AnswerRequest request, HttpServletResponse response) {
        long studentId = interviewPortalService.currentStudentId();
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType("text/event-stream;charset=UTF-8");
        SseEmitter emitter = new SseEmitter(GRADE_STREAM_TIMEOUT_MILLIS);
        emitter.onTimeout(emitter::complete);
        try {
            interviewGradeExecutor.execute(() -> streamGrade(emitter, studentId, id, request));
        } catch (RejectedExecutionException ex) {
            fail(emitter, ErrorCode.TOO_MANY_REQUESTS.code(), ErrorCode.TOO_MANY_REQUESTS.defaultMessage());
        }
        return emitter;
    }

    @PostMapping("/sessions/{id}/finish")
    public ApiResponse<FinishResult> finish(@PathVariable String id, @RequestBody(required = false) FinishRequest request,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(interviewPortalService.finish(id, request, idempotencyKey));
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<ReportView> report(@PathVariable long id) {
        return ApiResponse.ok(interviewPortalService.report(id));
    }

    @PostMapping("/reports/{id}/regenerate")
    public ApiResponse<ReportView> regenerate(@PathVariable long id) {
        return ApiResponse.ok(interviewPortalService.regenerate(id));
    }

    @GetMapping("/records/stats")
    public ApiResponse<InterviewStats> stats() {
        return ApiResponse.ok(interviewPortalService.stats());
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<RecordItem>> records(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        return ApiResponse.ok(interviewPortalService.records(page, pageSize));
    }

    private void streamGrade(SseEmitter emitter, long studentId, String sessionId, AnswerRequest request) {
        try {
            send(emitter, "status", Map.of("phase", "analyzing"));
            PreparedGrade prepared = interviewPortalService.prepareAnswer(studentId, sessionId, request);
            send(emitter, "status", Map.of("phase", "grading"));
            String feedback = prepared.feedback() == null ? "" : prepared.feedback();
            for (int i = 0; i < feedback.length(); i += TOKEN_CHUNK_CHARS) {
                send(emitter, "token", Map.of("delta", feedback.substring(i, Math.min(feedback.length(), i + TOKEN_CHUNK_CHARS))));
            }
            send(emitter, "grade", Map.of(
                    "order", prepared.order(),
                    "score", prepared.score(),
                    "feedback", feedback,
                    "degraded", prepared.degraded(),
                    "referencePoints", prepared.referencePoints() == null ? List.of() : prepared.referencePoints()));
            emitter.complete();
        } catch (BizException ex) {
            fail(emitter, ex.getErrorCode().code(), ex.getMessage());
        } catch (Exception ex) {
            fail(emitter, ErrorCode.AI_UNAVAILABLE.code(), "评分失败");
        }
    }

    private void fail(SseEmitter emitter, int code, String message) {
        try {
            send(emitter, "error", Map.of("code", code, "message", message == null ? "评分失败" : message));
        } catch (IOException ignored) {
            // 客户端已断开时不再回写。
        }
        emitter.complete();
    }

    private void send(SseEmitter emitter, String name, Object payload) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(payload)));
    }
}
