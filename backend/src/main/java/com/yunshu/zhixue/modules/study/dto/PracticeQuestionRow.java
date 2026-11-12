package com.yunshu.zhixue.modules.study.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PracticeQuestionRow {
    private Long id;
    private Integer type;
    private Integer difficulty;
    private String content;
    private String answer;
    private String analysis;
    private Long stageId;
    private String stageName;
    private Long subjectId;
    private String subjectName;
}
