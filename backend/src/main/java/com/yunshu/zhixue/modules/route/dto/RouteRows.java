package com.yunshu.zhixue.modules.route.dto;

import lombok.Getter;
import lombok.Setter;

public final class RouteRows {

    private RouteRows() {
    }

    @Getter
    @Setter
    public static class CourseLink {
        private Long routeCourseId;
        private Long routeId;
        private Long courseId;
        private Integer sortOrder;
        private String name;
        private String bvid;
        private Integer durationMinutes;
        private String coverUrl;
        private String link;
        private Integer source;
        private Long subjectId;
    }

    @Getter
    @Setter
    public static class ProgressCount {
        private Long routeId;
        private Long completedCount;
    }
}
