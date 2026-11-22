package com.yunshu.zhixue.modules.study.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class PracticePayloads {

    private PracticePayloads() {
    }

    public record PracticeStartRequest(String source, Long questionId, Long subjectId, Long stageId,
                                       Integer type, Integer difficulty, Integer count, Integer mode,
                                       List<Long> questionIds) {
    }

    public record PracticeOption(String key, String text) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PracticeQuestionCard(long id, int type, int difficulty, String content,
                                       List<PracticeOption> options, String answer) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PracticeStartResult(String practiceId, String title, Long subjectId, Long stageId,
                                      List<PracticeQuestionCard> questions) {
    }

    public record PracticeAnswerInput(Long questionId, String answer, Integer durationSeconds) {
    }

    public record PracticeSubmitRequest(List<PracticeAnswerInput> answers) {
    }

    public record PracticeGradeRequest(Long questionId, String answer) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PracticeResultItem(long questionId, @JsonProperty("isCorrect") boolean correct, int score,
                                     String answer, String analysis, String aiFeedback, Boolean degraded,
                                     String degradeReason, List<String> referencePoints) {
    }

    public record PracticeSubmitResult(List<PracticeResultItem> results) {
    }

    public record PaperQuestion(long id, int type, String answer, String analysis, String content) {
    }

    public record CachedGrade(long questionId, String answer, int score, String feedback, boolean degraded,
                              String degradeReason, List<String> referencePoints) {
    }

    public record Paper(long studentId, int mode, List<PaperQuestion> questions, List<CachedGrade> grades) {
    }

    public record PreparedGrade(int order, GradeView grade) {
    }

    public record GradeView(int score, String feedback, boolean degraded, String degradeReason,
                            List<String> referencePoints) {
    }
}
