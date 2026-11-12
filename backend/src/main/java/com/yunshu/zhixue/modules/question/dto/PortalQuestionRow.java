package com.yunshu.zhixue.modules.question.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PortalQuestionRow {
    private Long id;
    private Integer type;
    private Integer difficulty;
    private String content;
    private String answer;
    private String analysis;
    private String tags;
    private String subjectName;
    private String stageName;
    private Long doneCount;
    private Long correctCount;
}
