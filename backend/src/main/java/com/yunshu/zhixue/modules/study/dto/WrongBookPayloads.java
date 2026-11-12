package com.yunshu.zhixue.modules.study.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public final class WrongBookPayloads {

    private WrongBookPayloads() {
    }

    public record WrongOption(String key, String text) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WrongQuestionItem(long id, long questionId, int type, String content, List<WrongOption> options,
                                    String correctAnswer, String userAnswer, String analysis, int errorCount,
                                    int correctedCount, int status, LocalDateTime lastWrongAt, String subjectName,
                                    String stageName) {
    }

    public record WrongTopItem(long id, long questionId, String content, int errorCount) {
    }

    public record WrongStats(long total, long toReviewCount, long masteredCount, int masteryRate,
                             List<WrongTopItem> top3) {
    }

    public record RedoStartRequest(List<Long> ids) {
    }
}
