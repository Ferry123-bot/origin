package com.yunshu.zhixue.modules.study.controller.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeGradeRequest;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeStartRequest;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeStartResult;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeSubmitRequest;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeSubmitResult;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PreparedGrade;
import com.yunshu.zhixue.modules.study.service.PracticePortalService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/portal/v1/practice")
public class PortalPracticeController {

    private final PracticePortalService practicePortalService;
    private final ObjectMapper objectMapper;

    public PortalPracticeController(PracticePortalService practicePortalService, ObjectMapper objectMapper) {
        this.practicePortalService = practicePortalService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/start")
    public ApiResponse<PracticeStartResult> start(@RequestBody PracticeStartRequest request) {
        return ApiResponse.ok(practicePortalService.start(request));
    }

    @PostMapping("/{practiceId}/submit")
    public ApiResponse<PracticeSubmitResult> submit(@PathVariable String practiceId,
                                                    @RequestBody PracticeSubmitRequest request) {
        return ApiResponse.ok(practicePortalService.submit(practiceId, request));
    }

    @PostMapping(value = "/{practiceId}/grade", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter grade(@PathVariable String practiceId, @RequestBody PracticeGradeRequest request,
                            HttpServletResponse response) {
        PreparedGrade prepared = practicePortalService.prepareGrade(practiceId, request);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> streamGrade(emitter, prepared));
        return emitter;
    }

    private void streamGrade(SseEmitter emitter, PreparedGrade prepared) {
        try {
            send(emitter, "status", Map.of("phase", "analyzing"));
            Thread.sleep(350);
            send(emitter, "status", Map.of("phase", "grading"));
            String feedback = prepared.grade().feedback();
            for (int i = 0; i < feedback.length(); i += 8) {
                send(emitter, "token", Map.of("delta", feedback.substring(i, Math.min(feedback.length(), i + 8))));
            }
            Map<String, Object> gradePayload = new java.util.LinkedHashMap<>();
            gradePayload.put("order", prepared.order());
            gradePayload.put("score", prepared.grade().score());
            gradePayload.put("feedback", feedback);
            gradePayload.put("degraded", prepared.grade().degraded());
            gradePayload.put("referencePoints", prepared.grade().referencePoints());
            if (prepared.grade().degradeReason() != null && !prepared.grade().degradeReason().isBlank()) {
                gradePayload.put("degradeReason", prepared.grade().degradeReason());
            }
            send(emitter, "grade", gradePayload);
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
