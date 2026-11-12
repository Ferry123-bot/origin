package com.yunshu.zhixue.modules.student.dto;

import lombok.Getter;
import lombok.Setter;

public final class ProfileRows {

    private ProfileRows() {
    }

    @Getter
    @Setter
    public static class PracticeTotal {
        private Long totalCount;
        private Long correctCount;
    }
}
