package com.yunshu.zhixue.modules.home.dto;

import lombok.Getter;
import lombok.Setter;

public final class HomeRows {

    private HomeRows() {
    }

    @Getter
    @Setter
    public static class HotStageRow {
        private Long stageId;
        private Long subjectId;
        private String subjectName;
        private String stageName;
        private String icon;
        private Long questionCount;
    }

    @Getter
    @Setter
    public static class PracticeTotal {
        private Long totalCount;
        private Long correctCount;
    }

    @Getter
    @Setter
    public static class PracticeDay {
        private String day;
        private Long cnt;
    }
}
