package com.yunshu.zhixue.modules.interview.service;

import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.Dimension;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.ImproveCard;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.Weakness;
import com.yunshu.zhixue.modules.interview.dto.InterviewSessionState.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 报告由逐题得分汇总，不再单独请求模型。同一场面试重复生成，结论一致。
 */
public final class InterviewReportComposer {

    private InterviewReportComposer() {
    }

    public record Content(List<Dimension> dimensions, List<String> strengths, List<Weakness> weaknesses,
                          List<String> suggestions, List<ImproveCard> cards) {
    }

    public static Content compose(String subjectName, int difficulty, List<Item> questions) {
        String subject = subjectName == null || subjectName.isBlank() ? "本方向" : subjectName.trim();
        List<Item> items = questions == null ? List.of() : questions;
        int total = Math.max(1, items.size());
        double average = items.stream().mapToInt(item -> item.getScore() == null ? 0 : item.getScore()).average().orElse(0);
        long answered = items.stream().filter(InterviewReportComposer::answered).count();
        double averageLength = items.stream().mapToInt(item -> text(item.getStudentAnswer()).length()).average().orElse(0);
        List<Dimension> dimensions = dimensions(difficulty, total, average, answered, averageLength);
        return new Content(dimensions, strengths(subject, dimensions, average, answered, total),
                weaknesses(items), suggestions(subject, dimensions), cards(subject, difficulty));
    }

    private static List<Dimension> dimensions(int difficulty, int total, double average, long answered, double averageLength) {
        int knowledge = scale(average * 10);
        int understanding = scale(answered * 45.0 / total + average * 5.5);
        int expression = scale(Math.min(averageLength, 160) / 160.0 * 45 + average * 5.5);
        int practice = scale(average * 7 + (answered == total ? 30 : answered * 30.0 / total));
        int completeness = scale(answered * 55.0 / total + average * 4.5);
        double depthFactor = difficulty >= 3 ? 1.08 : difficulty <= 1 ? 0.92 : 1;
        int depth = scale(average * 10 * depthFactor);
        return List.of(
                new Dimension("专业知识", knowledge),
                new Dimension("问题理解", understanding),
                new Dimension("逻辑表达", expression),
                new Dimension("实践应用", practice),
                new Dimension("回答完整度", completeness),
                new Dimension("综合深度", depth));
    }

    private static List<String> strengths(String subject, List<Dimension> dimensions, double average, long answered, int total) {
        List<String> lines = new ArrayList<>();
        if (average >= 8) {
            lines.add("整体回答覆盖了主要要点，表达比较稳定。");
        }
        if (answered == total) {
            lines.add("题目全部作答，没有留下空题。");
        }
        dimensions.stream()
                .filter(item -> item.score() >= 80)
                .map(item -> item.name() + "表现较好，对「" + subject + "」相关问题有比较清楚的认识。")
                .limit(2)
                .forEach(lines::add);
        if (lines.isEmpty()) {
            lines.add("能够完成作答，并给出了自己的理解。");
        }
        return lines.stream().distinct().limit(3).toList();
    }

    private static List<Weakness> weaknesses(List<Item> items) {
        List<Weakness> lines = items.stream()
                .filter(item -> item.getScore() == null || item.getScore() < 6)
                .sorted(Comparator.comparingInt(item -> item.getScore() == null ? 0 : item.getScore()))
                .limit(3)
                .map(InterviewReportComposer::weaknessOf)
                .toList();
        if (!lines.isEmpty()) {
            return lines;
        }
        return List.of(new Weakness("回答里的例子还可以更具体", "建议每题至少补一个你亲手做过的场景，说明输入、做法和结果。"));
    }

    private static Weakness weaknessOf(Item item) {
        String brief = abbreviate(item.getContent());
        String text = "第" + item.getOrder() + "题得分偏低：" + brief;
        String feedback = text(item.getFeedback());
        String advice;
        if (feedback.isBlank() || "未作答".equals(feedback)) {
            advice = "未作答会记 0 分。建议先写下结论，再补一个例子。";
        } else {
            advice = feedback;
        }
        return new Weakness(text, advice);
    }

    private static List<String> suggestions(String subject, List<Dimension> dimensions) {
        Dimension lowest = dimensions.stream().min(Comparator.comparingInt(Dimension::score)).orElse(dimensions.get(0));
        return List.of(
                "回答时先给结论，再用一个自己做过的例子说明，重点补强「" + lowest.name() + "」。",
                "对照低分题目，把没覆盖到的概念放进「" + subject + "」题库里再练一遍。",
                "同一方向再进行一场面试，对比两次报告里下降的维度。");
    }

    private static List<ImproveCard> cards(String subject, int difficulty) {
        String again = difficulty < 3
                ? "保持练习节奏，用「" + subject + "」提高一档难度再练一场，两次报告对比着看进步更清楚。"
                : "保持练习节奏，用「" + subject + "」同一难度再练一场，两次报告对比着看进步更清楚。";
        return List.of(
                new ImproveCard("推荐题目", "上面待提升的知识点，可以在题库中心「" + subject + "」方向下找到同类题，重点把得分低的几类再做一遍。"),
                new ImproveCard("推荐课程", "按本次面试方向进入学习路线，优先补齐待提升项相关的课程，按顺序学比跳着学更稳。"),
                new ImproveCard("推荐再次面试", again));
    }

    private static boolean answered(Item item) {
        return !text(item.getStudentAnswer()).isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String abbreviate(String content) {
        String compact = text(content).replaceAll("\\s+", "");
        if (compact.length() <= 24) {
            return compact.isEmpty() ? "本题" : compact;
        }
        return compact.substring(0, 24) + "…";
    }

    private static int scale(double value) {
        return Math.max(0, Math.min(100, (int) Math.round(value)));
    }
}
