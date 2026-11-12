package com.yunshu.zhixue.modules.study.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WrongQuestionRow {
    private Long id;
    private Long questionId;
    private Integer type;
    private String content;
    private String answer;
    private String analysis;
    private Integer errorCount;
    private Integer correctedCount;
    private Integer status;
    private LocalDateTime lastWrongAt;
    private String lastAnswer;
    private String subjectName;
    private String stageName;
}
