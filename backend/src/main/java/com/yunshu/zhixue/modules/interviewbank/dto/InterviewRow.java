package com.yunshu.zhixue.modules.interviewbank.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InterviewRow {
    private Long id;
    private Long subjectId;
    private String subjectName;
    private String content;
    private String referenceAnswer;
    private String topic;
    private Integer difficulty;
    private String tags;
    private Integer sourceType;
    private String sourceDocName;
    private Integer sourceSeq;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
