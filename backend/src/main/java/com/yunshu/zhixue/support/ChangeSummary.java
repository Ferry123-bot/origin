package com.yunshu.zhixue.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 把一次修改收成「字段从旧值改为新值」的说明，没变化的字段不写入。 */
public final class ChangeSummary {

    private static final int CLIP = 80;
    private final List<String> parts = new ArrayList<>();

    public ChangeSummary change(String label, Object before, Object after) {
        String left = show(before);
        String right = show(after);
        if (left.equals(right)) {
            return this;
        }
        parts.add(label + "从「" + clip(left) + "」改为「" + clip(right) + "」");
        return this;
    }

    public ChangeSummary note(String text) {
        if (text != null && !text.isBlank()) {
            parts.add(text.trim());
        }
        return this;
    }

    public String finish(String title) {
        if (parts.isEmpty()) {
            return title + "，保存时字段没有变化";
        }
        return title + "：" + String.join("；", parts);
    }

    private static String show(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replaceAll("\\s+", " ").trim();
    }

    private static String clip(String value) {
        if (value.isEmpty()) {
            return "空";
        }
        return value.length() <= CLIP ? value : value.substring(0, CLIP) + "…";
    }

    public static boolean same(Object left, Object right) {
        return Objects.equals(show(left), show(right));
    }
}
