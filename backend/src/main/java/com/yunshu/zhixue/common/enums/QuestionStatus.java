package com.yunshu.zhixue.common.enums;

public enum QuestionStatus {
    PUBLISHED(1),
    UNPUBLISHED(2),
    PENDING(3),
    DRAFT(4),
    REJECTED(5);

    private final int code;

    QuestionStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
