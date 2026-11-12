package com.yunshu.zhixue.modules.interviewbank.service;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.Sample;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InterviewMarkdownParser {

    public static final int MAX_ITEMS = 500;
    public static final int MAX_TEXT = 20_000;

    private static final Pattern HEADING = Pattern.compile("^#{1,3}(?!#)\\s+(.+?)\\s*$");
    private static final Pattern NUMBERING = Pattern.compile("^(\\d+[.、)]|[一二三四五六七八九十百零]+、)\\s*");

    public List<Sample> parse(String raw) {
        String text = raw == null ? "" : raw;
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<Draft> drafts = new ArrayList<>();
        String[] lines = text.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher heading = HEADING.matcher(lines[i]);
            if (heading.matches()) {
                String title = stripNumbering(heading.group(1).trim());
                if (!title.isBlank()) {
                    drafts.add(new Draft(title, i + 1));
                }
                continue;
            }
            if (!drafts.isEmpty() && !lines[i].isBlank()) {
                drafts.get(drafts.size() - 1).answerLines.add(lines[i].trim());
            }
        }
        if (drafts.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "这个文件里没解析出题目，题干需要是 # 到 ### 标题");
        }
        if (drafts.size() > MAX_ITEMS) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "单次最多导入 500 道题，这个文件解析出 " + drafts.size() + " 道");
        }
        List<Sample> items = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            if (draft.content.length() > MAX_TEXT) {
                throw new BizException(ErrorCode.IMPORT_INVALID, "第 " + draft.lineNo + " 行题干超过 " + MAX_TEXT + " 字");
            }
            String answer = String.join("\n", draft.answerLines);
            if (answer.length() > MAX_TEXT) {
                throw new BizException(ErrorCode.IMPORT_INVALID, "第 " + draft.lineNo + " 行附近的参考答案超过 " + MAX_TEXT + " 字");
            }
            items.add(new Sample(draft.content, answer, draft.lineNo));
        }
        return items;
    }

    private static String stripNumbering(String title) {
        return NUMBERING.matcher(title).replaceFirst("");
    }

    private static final class Draft {
        private final String content;
        private final int lineNo;
        private final List<String> answerLines = new ArrayList<>();

        private Draft(String content, int lineNo) {
            this.content = content;
            this.lineNo = lineNo;
        }
    }
}
