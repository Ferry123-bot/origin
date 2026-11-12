package com.yunshu.zhixue.modules.interviewbank.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopicCountRow {
    private String topic;
    private Long subjectId;
    private Long questionCount;
}
