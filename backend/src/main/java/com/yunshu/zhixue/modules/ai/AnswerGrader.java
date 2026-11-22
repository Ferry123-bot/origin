package com.yunshu.zhixue.modules.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 简答判分。已配置大模型时按模型结果给 0–10 分；模型不可用或返回无法解析时，
 * 按参考答案关键词判分并标记降级。同一题干、参考答案和作答的关键词结果是确定的。
 */
@Component
public class AnswerGrader {

    private static final String SYSTEM_PROMPT = """
            你是简答题评分员。只输出一个 JSON 对象，不要 Markdown，不要解释。
            字段：score 为 0 到 10 的整数；feedback 为 80 字以内的中文评语；referencePoints 为最多 4 条要点，每条不超过 20 字。
            对照参考答案判断对错和完整度。答非所问或明显错误给低分，覆盖主要要点给高分。
            """;

    private final ModelChatClient modelChatClient;
    private final ObjectMapper objectMapper;

    public AnswerGrader(ModelChatClient modelChatClient, ObjectMapper objectMapper) {
        this.modelChatClient = modelChatClient;
        this.objectMapper = objectMapper;
    }

    public GradeResult grade(String question, String reference, String userAnswer) {
        String answer = userAnswer == null ? "" : userAnswer.trim();
        if (answer.isEmpty()) {
            return new GradeResult(0, "未作答", List.of(), true, "未作答");
        }
        GradeResult model = gradeByModel(question, reference, answer);
        if (model != null) {
            return model;
        }
        GradeResult fallback = gradeByKeywords(reference, answer);
        String reason = modelChatClient.enabled()
                ? blankTo(modelChatClient.lastFailureReason(), "大模型返回无法解析")
                : "未配置大模型";
        return new GradeResult(fallback.score(), fallback.feedback(), fallback.referencePoints(), true, reason);
    }

    private GradeResult gradeByModel(String question, String reference, String answer) {
        if (!modelChatClient.enabled()) {
            return null;
        }
        String raw = modelChatClient.complete(SYSTEM_PROMPT, prompt(question, reference, answer));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        GradeResult parsed = parseModel(raw);
        if (parsed == null) {
            modelChatClient.noteFailure("大模型返回无法解析");
            return null;
        }
        return parsed;
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String prompt(String question, String reference, String answer) {
        return "题目：" + clip(question) + "\n参考答案：" + clip(reference) + "\n考生回答：" + clip(answer);
    }

    private GradeResult parseModel(String raw) {
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            JsonNode scoreNode = node.get("score");
            if (scoreNode == null || !scoreNode.isNumber()) {
                return null;
            }
            int score = (int) Math.round(scoreNode.asDouble());
            if (score < 0 || score > 10) {
                return null;
            }
            String feedback = node.path("feedback").asText("").replaceAll("\\s+", " ").trim();
            if (feedback.isEmpty()) {
                feedback = "已完成评分。";
            } else if (feedback.length() > 200) {
                feedback = feedback.substring(0, 200);
            }
            List<String> points = new ArrayList<>();
            JsonNode rawPoints = node.get("referencePoints");
            if (rawPoints != null && rawPoints.isArray()) {
                for (JsonNode point : rawPoints) {
                    String label = point.asText("").replaceAll("\\s+", "").trim();
                    if (label.isEmpty()) {
                        continue;
                    }
                    points.add(label.length() > 40 ? label.substring(0, 40) : label);
                    if (points.size() >= 4) {
                        break;
                    }
                }
            }
            return new GradeResult(score, feedback, points, false, null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractJson(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private GradeResult gradeByKeywords(String reference, String answer) {
        String ref = reference == null ? "" : reference.trim();
        String normAnswer = normalize(answer);
        String normRef = normalize(ref);
        if (!normRef.isEmpty() && normAnswer.contains(normRef)) {
            return new GradeResult(10, "回答覆盖了参考答案的主要内容。",
                    pointsOf(ref).stream().map(Point::label).toList(), true, null);
        }
        List<Point> points = pointsOf(ref);
        if (points.isEmpty()) {
            int score = normAnswer.length() >= 30 ? 6 : 3;
            return new GradeResult(score, "参考答案不足，已按作答完整度估算。", List.of("作答完整"), true, null);
        }
        List<String> hits = new ArrayList<>();
        for (Point point : points) {
            if (normAnswer.contains(normalize(point.probe()))) {
                hits.add(point.label());
            }
        }
        int score = (int) Math.round(hits.size() * 10.0 / points.size());
        score = Math.max(0, Math.min(10, score));
        String feedback;
        if (score >= 8) {
            feedback = "回答覆盖了主要要点，表述清楚。";
        } else if (score >= 6) {
            feedback = "回答基本正确，还可以再补充部分要点。";
        } else {
            feedback = "回答与参考答案相差较多，建议对照解析复习。";
        }
        List<String> shown = hits.isEmpty()
                ? points.stream().limit(3).map(Point::label).toList()
                : hits;
        return new GradeResult(score, feedback, shown, true, null);
    }

    private List<Point> pointsOf(String reference) {
        if (reference == null || reference.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Point> points = new ArrayList<>();
        String[] segments = reference.split("[，。；、！？：\\n\\r,.;!?:（）()【】]+");
        for (String segment : segments) {
            String text = segment.replaceAll("\\s+", "").replaceAll("^[0-9]+", "").trim();
            if (text.length() < 2 || !seen.add(text)) {
                continue;
            }
            String label = text.length() > 18 ? text.substring(0, 18) : text;
            String probe = text.length() <= 12 ? text : text.substring(0, 8);
            points.add(new Point(label, probe));
            if (points.size() >= 8) {
                break;
            }
        }
        return points;
    }

    private record Point(String label, String probe) {
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String clip(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }
}
