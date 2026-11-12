package com.yunshu.zhixue.modules.stats.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubjectQuestionRow {

    private Long subjectId;
    private String name;
    private String themeColor;
    private Long questionCount;
}
