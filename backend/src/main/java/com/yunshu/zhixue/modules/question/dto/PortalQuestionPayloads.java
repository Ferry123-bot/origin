package com.yunshu.zhixue.modules.question.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public final class PortalQuestionPayloads {

    private PortalQuestionPayloads() {
    }

    public record PortalQuestionItem(
            long id,
            int type,
            int difficulty,
            String content,
            String subjectName,
            String stageName,
            String tags,
            int doneCount,
            int accuracyRate
    ) {
    }

    public record OptionItem(String key, String text) {
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PortalQuestionDetail {
        private long id;
        private int type;
        private int difficulty;
        private String content;
        private String subjectName;
        private String stageName;
        private String tags;
        private List<OptionItem> options;
        private String answer;
        private String analysis;
        private Boolean inWrongBook;
    }
}
