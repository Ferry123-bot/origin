package com.yunshu.zhixue.common.enums;

public enum QuestionType {
    SINGLE(1),
    MULTIPLE(2),
    JUDGE(3),
    SHORT_ANSWER(4);

    private final int code;

    QuestionType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
