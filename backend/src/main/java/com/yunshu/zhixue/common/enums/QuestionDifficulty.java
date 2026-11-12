package com.yunshu.zhixue.common.enums;

public enum QuestionDifficulty {
    EASY(1),
    MEDIUM(2),
    HARD(3);

    private final int code;

    QuestionDifficulty(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
