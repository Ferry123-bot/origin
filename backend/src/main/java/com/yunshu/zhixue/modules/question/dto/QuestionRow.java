package com.yunshu.zhixue.modules.question.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class QuestionRow {
    private Long id;
    private Long stageId;
    private String stageName;
    private Long subjectId;
    private String subjectName;
    private Integer type;
    private Integer difficulty;
    private String content;
    private String answer;
    private String analysis;
    private String tags;
    private Integer status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private Long doneCount;
    private Long correctCount;
}
