package com.yunshu.zhixue.modules.stats.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OverviewRow {

    private Long subjectTotal;
    private Long subjectToday;
    private Long subjectYesterday;
    private Long questionTotal;
    private Long questionToday;
    private Long questionYesterday;
    private Long studentTotal;
    private Long studentToday;
    private Long studentYesterday;
    private Long interviewTotal;
    private Long interviewToday;
    private Long interviewYesterday;
}
