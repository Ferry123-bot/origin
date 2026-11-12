package com.yunshu.zhixue.modules.interview.controller.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Validated
@RestController
@RequestMapping("/api/portal/v1/interview")
public class PortalInterviewController {

    private final InterviewPortalService interviewPortalService;
    private final ObjectMapper objectMapper;

    public PortalInterviewController(InterviewPortalService interviewPortalService, ObjectMapper objectMapper) {
        this.interviewPortalService = interviewPortalService;
        this.objectMapper = objectMapper;
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
        PreparedGrade prepared = interviewPortalService.prepareAnswer(id, request);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType("text/event-stream;charset=UTF-8");
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> streamGrade(emitter, prepared));
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

    private void streamGrade(SseEmitter emitter, PreparedGrade prepared) {
        try {
            send(emitter, "status", Map.of("phase", "analyzing"));
            Thread.sleep(280);
            send(emitter, "status", Map.of("phase", "grading"));
            String feedback = prepared.feedback() == null ? "" : prepared.feedback();
            for (int i = 0; i < feedback.length(); i += 8) {
                send(emitter, "token", Map.of("delta", feedback.substring(i, Math.min(feedback.length(), i + 8))));
                Thread.sleep(30);
            }
            send(emitter, "status", Map.of("phase", "thinking"));
            Thread.sleep(160);
            send(emitter, "grade", Map.of(
                    "order", prepared.order(),
                    "score", prepared.score(),
                    "feedback", feedback,
                    "degraded", prepared.degraded(),
                    "referencePoints", prepared.referencePoints()));
            emitter.complete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Exception ex) {
            try {
                send(emitter, "error", Map.of("code", 20010, "message", "评分失败"));
            } catch (IOException ignored) {
                // 客户端已断开时不再回写。
            }
            emitter.complete();
        }
    }

    private void send(SseEmitter emitter, String name, Object payload) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(payload)));
    }
}
