package com.yunshu.zhixue.modules.interview.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InterviewStatsRow {

    private Long interviewTotal;
    private BigDecimal avgScore;
    private BigDecimal maxScore;
    private Long totalDuration;
}
