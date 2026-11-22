package com.yunshu.zhixue.modules.ai;

import java.util.List;

/** 简答评分结果。degraded 为 true 表示未使用大模型，已按关键词规则判分。 */
public record GradeResult(
        int score,
        String feedback,
        List<String> referencePoints,
        boolean degraded,
        String degradeReason
) {

    public GradeResult(int score, String feedback, List<String> referencePoints, boolean degraded) {
        this(score, feedback, referencePoints, degraded, null);
    }
}
