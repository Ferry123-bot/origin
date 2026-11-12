package com.yunshu.zhixue.modules.interview.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class InterviewRecordRow {

    private Long id;
    private Long subjectId;
    private String subjectName;
    private Integer difficulty;
    private Integer questionCount;
    private BigDecimal overallScore;
    private Integer totalDuration;
    private LocalDateTime completedAt;
    private Long reportId;
}
