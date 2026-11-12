package com.yunshu.zhixue.modules.study.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WrongBookStatsRow {
    private Long total;
    private Long toReviewCount;
    private Long masteredCount;
}
