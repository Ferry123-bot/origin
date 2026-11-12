package com.yunshu.zhixue.modules.interview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 面试进行中的 Redis 暂存。分数只在服务端写入，结束时整场落库。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterviewSessionState {

    private long studentId;
    private long subjectId;
    private String subjectName;
    private int difficulty;
    private int questionCount;
    private LocalDateTime startedAt;
    private List<Item> questions = new ArrayList<>();

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private int order;
        private Long questionId;
        private long subjectId;
        private int difficulty;
        private String content;
        private String referenceAnswer;
        private String source;
        private Integer score;
        private String feedback;
        private Boolean degraded;
        private List<String> referencePoints = new ArrayList<>();
        private String studentAnswer;
        private Integer durationSeconds;
    }
}
