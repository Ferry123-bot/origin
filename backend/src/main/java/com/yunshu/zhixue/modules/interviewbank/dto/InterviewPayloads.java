package com.yunshu.zhixue.modules.interviewbank.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class InterviewPayloads {

    private InterviewPayloads() {
    }

    public record SubjectOption(Long id, String name) {
    }

    public record QuestionItem(Long id, String content, Long subjectId, String subjectName, Integer difficulty,
                               String topic, String tags, Integer sourceType, String sourceDocName, Integer sourceSeq,
                               String createdAt) {
    }

    public record QuestionDetail(Long id, String content, String referenceAnswer, Long subjectId, String subjectName,
                                 Integer difficulty, String topic, String tags, Integer sourceType, String sourceDocName,
                                 Integer sourceSeq, String createdAt, String updatedAt) {
    }

    public record QuestionCreateRequest(
            @NotNull Long subjectId,
            @NotBlank @Size(max = 20000) String content,
            @Size(max = 20000) String referenceAnswer,
            @Size(max = 50) String topic,
            @NotNull @Min(1) @Max(3) Integer difficulty,
            @Size(max = 255) String tags) {
    }

    public record QuestionUpdateRequest(
            @Size(max = 50) String topic,
            @NotNull @Min(1) @Max(3) Integer difficulty) {
    }

    public record Sample(String content, String referenceAnswer, int lineNo) {
    }

    public record ImportPreview(String token, String fileName, long fileSize, int total, List<Sample> samples) {
    }

    public record ImportConfirmRequest(
            @NotBlank String token,
            @NotNull Long subjectId,
            @Size(max = 50) String topic,
            @NotNull @Min(1) @Max(3) Integer difficulty,
            @Size(max = 255) String tags) {
    }

    public record ImportConfirmResult(int imported) {
    }

    public record UploadAccepted(String taskId) {
    }

    public record UploadStatus(String status, int parsedCount, List<Sample> samples, String errorMessage) {
    }

    public record SearchRequest(@NotBlank @Size(max = 100) String keyword) {
    }

    public record SearchHit(Long id, String content, String subjectName, Integer sourceType, String sourceDocName,
                            Integer sourceSeq, String topic, Integer difficulty) {
    }

    public record TopicOption(Long id, String name, Long subjectId, String subjectName, long questionCount,
                              boolean inVocabulary, int sortOrder) {
    }

    public record TopicSaveRequest(
            @NotBlank @Size(max = 50) String name,
            Long subjectId,
            @Min(0) @Max(9999) Integer sortOrder) {
    }
}
