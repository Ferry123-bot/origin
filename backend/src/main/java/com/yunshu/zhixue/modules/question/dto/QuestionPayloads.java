package com.yunshu.zhixue.modules.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class QuestionPayloads {

    private QuestionPayloads() {
    }

    public record QuestionStats(long total, long published, long pending, long single, long multiple, long judge, long shortAnswer) {
    }

    public record QuestionItem(long id, String content, long stageId, String stageName, String subjectName,
                               int type, int difficulty, int status, String createdAt,
                               long doneCount, Double accuracyRate) {
    }

    public record OptionView(String key, String text, boolean correct) {
    }

    public record QuestionDetail(long id, String content, long stageId, String stageName, String subjectName,
                                 int type, int difficulty, String answer, String analysis, String tags, int status,
                                 String rejectReason, String createdAt, List<OptionView> options,
                                 long doneCount, long correctCount, Double accuracyRate) {
    }

    public record StageOption(long id, String name, long subjectId, String subjectName) {
    }

    public record OptionInput(@NotBlank @Size(max = 4) String key, @NotBlank @Size(max = 500) String text,
                              @NotNull Boolean correct) {
    }

    public record QuestionSaveRequest(
            @NotBlank @Size(max = 20000) String content,
            @NotNull Long stageId,
            @NotNull @Min(1) @Max(4) Integer type,
            @NotNull @Min(1) @Max(3) Integer difficulty,
            @Size(max = 255) String tags,
            @Size(max = 20000) String analysis,
            @Size(max = 20000) String answer,
            @Valid List<OptionInput> options) {
    }

    public record AuditRequest(@NotNull Boolean pass, @Size(max = 255) String rejectReason) {
    }

    public record StatusRequest(@NotBlank String action) {
    }

    public record BatchStatusRequest(@NotEmpty @Size(max = 200) List<Long> ids, @NotBlank String action) {
    }

    public record BatchIdsRequest(@NotEmpty @Size(max = 200) List<Long> ids) {
    }

    public record ImportRow(String sheet, int row, String content, String type, String stage, String status, List<String> messages) {
    }

    public record ImportPreview(String token, int total, int valid, int invalid, List<ImportRow> rows) {
    }

    public record ImportConfirmRequest(@NotBlank String token) {
    }

    public record ImportConfirmResult(int imported) {
    }

    public record BatchResult(int affected, int skipped) {
    }
}
