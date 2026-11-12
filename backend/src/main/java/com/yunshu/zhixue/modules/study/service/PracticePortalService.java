package com.yunshu.zhixue.modules.study.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.ai.AnswerGrader;
import com.yunshu.zhixue.modules.ai.GradeResult;
import com.yunshu.zhixue.modules.question.entity.QuestionOption;
import com.yunshu.zhixue.modules.question.mapper.QuestionOptionMapper;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.CachedGrade;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.GradeView;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.Paper;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PaperQuestion;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeAnswerInput;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeGradeRequest;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeOption;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeQuestionCard;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeResultItem;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeStartRequest;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeStartResult;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeSubmitRequest;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeSubmitResult;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PreparedGrade;
import com.yunshu.zhixue.modules.study.dto.PracticeQuestionRow;
import com.yunshu.zhixue.modules.study.entity.PracticeRecord;
import com.yunshu.zhixue.modules.study.mapper.PracticeQuestionMapper;
import com.yunshu.zhixue.modules.study.mapper.PracticeRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PracticePortalService {

    private static final Duration PAPER_TTL = Duration.ofHours(24);
    private static final int MAX_RETURN = 100;
    private static final int MAX_SCOPED = 300;
    private static final int ANSWER_LIMIT = 5000;

    private final PracticeQuestionMapper practiceQuestionMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final StudentMapper studentMapper;
    private final AnswerGrader answerGrader;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public PracticePortalService(PracticeQuestionMapper practiceQuestionMapper, PracticeRecordMapper practiceRecordMapper,
                                 QuestionOptionMapper questionOptionMapper, StudentMapper studentMapper,
                                 AnswerGrader answerGrader, StringRedisTemplate redis, ObjectMapper objectMapper,
                                 JdbcTemplate jdbc) {
        this.practiceQuestionMapper = practiceQuestionMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.studentMapper = studentMapper;
        this.answerGrader = answerGrader;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public PracticeStartResult start(PracticeStartRequest request) {
        long studentId = requireStudentId();
        int mode = requireMode(request.mode());
        String source = request.source() == null ? "" : request.source().trim();
        Integer type = optionalScale(request.type(), 1, 4, "type");
        Integer difficulty = optionalScale(request.difficulty(), 1, 3, "difficulty");
        Integer count = request.count();
        if (count != null && (count < 1 || count > MAX_RETURN)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "count 需在 1 到 100 之间");
        }

        List<PracticeQuestionRow> rows;
        if ("question".equals(source)) {
            if (request.questionId() == null) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "请指定题目");
            }
            rows = practiceQuestionMapper.list(null, null, null, null, request.questionId(), null);
            if (rows.isEmpty()) {
                throw new BizException(ErrorCode.NOT_FOUND, "题目不存在或未上架");
            }
        } else if ("subject".equals(source)) {
            rows = practiceQuestionMapper.list(request.subjectId(), request.stageId(), type, difficulty, null, null);
        } else if ("wrong_redo".equals(source)) {
            rows = wrongRedoRows(studentId, request.questionIds());
        } else {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "source 无效");
        }
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_QUESTION_SHORTAGE);
        }

        boolean scoped = request.stageId() != null || request.subjectId() != null || request.questionId() != null
                || "wrong_redo".equals(source) || "question".equals(source);
        int limit = count != null ? count : (scoped ? MAX_SCOPED : MAX_RETURN);
        List<PracticeQuestionRow> selected = new ArrayList<>(rows);
        if (mode == 2) {
            Collections.shuffle(selected);
        }
        if (selected.size() > limit) {
            selected = new ArrayList<>(selected.subList(0, limit));
        }

        Map<Long, List<QuestionOption>> options = loadOptions(selected);
        List<PracticeQuestionCard> cards = new ArrayList<>();
        List<PaperQuestion> paperQuestions = new ArrayList<>();
        for (PracticeQuestionRow row : selected) {
            List<QuestionOption> rowOptions = options.getOrDefault(row.getId(), List.of());
            String answer = correctAnswer(row, rowOptions);
            cards.add(new PracticeQuestionCard(row.getId(), row.getType(), row.getDifficulty(), row.getContent(),
                    optionViews(row, rowOptions), answer));
            paperQuestions.add(new PaperQuestion(row.getId(), row.getType(), answer,
                    row.getAnalysis() == null ? "" : row.getAnalysis(),
                    row.getContent() == null ? "" : row.getContent()));
        }

        String practiceId = UUID.randomUUID().toString();
        savePaper(practiceId, new Paper(studentId, mode, paperQuestions, new ArrayList<>()));
        Long subjectId = sameId(selected, true);
        Long stageId = sameId(selected, false);
        String title = titleOf(selected, stageId, subjectId);
        return new PracticeStartResult(practiceId, title, subjectId, stageId, cards);
    }

    public PreparedGrade prepareGrade(String practiceId, PracticeGradeRequest request) {
        Paper paper = requirePaper(practiceId, requireStudentId());
        if (request.questionId() == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请指定题目");
        }
        String answer = normalizeAnswer(request.answer());
        if (answer.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请输入你的回答");
        }
        int index = indexOf(paper, request.questionId());
        PaperQuestion question = paper.questions().get(index);
        if (question.type() != 4) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "客观题请直接提交");
        }
        refresh(practiceId);
        GradeResult grade = gradeShortAnswer(question, answer, paper);
        savePaper(practiceId, remember(paper, question.id(), answer, grade));
        return new PreparedGrade(index + 1, new GradeView(grade.score(), grade.feedback(), grade.degraded(),
                grade.referencePoints()));
    }

    @Transactional
    public PracticeSubmitResult submit(String practiceId, PracticeSubmitRequest request) {
        long studentId = requireStudentId();
        Paper paper = requirePaper(practiceId, studentId);
        if (request.answers() == null || request.answers().isEmpty() || request.answers().size() > MAX_RETURN) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请提交 1 到 100 道题的作答");
        }
        List<Judged> judged = new ArrayList<>();
        for (PracticeAnswerInput input : request.answers()) {
            if (input.questionId() == null) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "请指定题目");
            }
            int index = indexOf(paper, input.questionId());
            PaperQuestion question = paper.questions().get(index);
            String answer = normalizeAnswer(input.answer());
            if (answer.isEmpty()) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "作答不能为空");
            }
            Integer duration = input.durationSeconds();
            if (duration != null && (duration < 0 || duration > 86400)) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "用时超出范围");
            }
            Judged judgedItem = judge(question, answer, duration, paper);
            judged.add(judgedItem);
            if (judgedItem.grade() != null) {
                paper = remember(paper, question.id(), answer, judgedItem.grade());
            }
        }
        savePaper(practiceId, paper);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<PracticeResultItem> results = new ArrayList<>();
        for (Judged item : judged) {
            PracticeRecord record = new PracticeRecord();
            record.setStudentId(studentId);
            record.setQuestionId(item.questionId);
            record.setMode(paper.mode());
            record.setIsCorrect(item.correct ? 1 : 0);
            record.setDurationSeconds(item.durationSeconds);
            record.setPracticedAt(now);
            record.setDeleteFlag(0);
            practiceRecordMapper.insert(record);
            if (item.correct) {
                markCorrect(studentId, item.questionId, now);
            } else {
                markWrong(studentId, item.questionId, item.userAnswer, now);
            }
            results.add(item.result);
        }
        refresh(practiceId);
        return new PracticeSubmitResult(results);
    }

    private Judged judge(PaperQuestion question, String userAnswer, Integer duration, Paper paper) {
        if (question.type() == 4) {
            GradeResult grade = gradeShortAnswer(question, userAnswer, paper);
            boolean correct = grade.score() >= 6;
            PracticeResultItem result = new PracticeResultItem(question.id(), correct, grade.score(),
                    question.answer(), blankToNull(question.analysis()), grade.feedback(), grade.degraded(),
                    grade.referencePoints().isEmpty() ? null : grade.referencePoints());
            return new Judged(question.id(), correct, userAnswer, duration, result, grade);
        }
        boolean correct = matches(question.type(), question.answer(), userAnswer);
        PracticeResultItem result = new PracticeResultItem(question.id(), correct, correct ? 10 : 0,
                question.answer(), blankToNull(question.analysis()), null, null, null);
        return new Judged(question.id(), correct, userAnswer, duration, result, null);
    }

    private GradeResult gradeShortAnswer(PaperQuestion question, String answer, Paper paper) {
        if (paper.grades() != null) {
            for (CachedGrade cached : paper.grades()) {
                if (cached.questionId() == question.id() && answer.equals(cached.answer())) {
                    List<String> points = cached.referencePoints() == null ? List.of() : cached.referencePoints();
                    return new GradeResult(cached.score(), cached.feedback(), points, cached.degraded());
                }
            }
        }
        return answerGrader.grade(question.content(), question.answer(), answer);
    }

    private Paper remember(Paper paper, long questionId, String answer, GradeResult grade) {
        List<CachedGrade> grades = new ArrayList<>();
        if (paper.grades() != null) {
            for (CachedGrade cached : paper.grades()) {
                if (cached.questionId() != questionId) {
                    grades.add(cached);
                }
            }
        }
        grades.add(new CachedGrade(questionId, answer, grade.score(), grade.feedback(), grade.degraded(),
                grade.referencePoints()));
        return new Paper(paper.studentId(), paper.mode(), paper.questions(), grades);
    }

    private List<PracticeQuestionRow> wrongRedoRows(long studentId, List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty() || questionIds.size() > MAX_RETURN) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择 1 到 100 道错题");
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : questionIds) {
            if (id == null || ids.contains(id)) {
                continue;
            }
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择 1 到 100 道错题");
        }
        List<PracticeQuestionRow> published = practiceQuestionMapper.list(null, null, null, null, null, ids);
        Map<Long, PracticeQuestionRow> byId = new LinkedHashMap<>();
        for (PracticeQuestionRow row : published) {
            byId.put(row.getId(), row);
        }
        Set<Long> inBook = activeWrongIds(studentId, ids);
        List<PracticeQuestionRow> ordered = new ArrayList<>();
        for (Long id : ids) {
            if (!byId.containsKey(id) || !inBook.contains(id)) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "包含不可重做的题目");
            }
            ordered.add(byId.get(id));
        }
        return ordered;
    }

    private Set<Long> activeWrongIds(long studentId, List<Long> questionIds) {
        String placeholders = String.join(",", questionIds.stream().map(id -> "?").toList());
        Object[] args = new Object[questionIds.size() + 1];
        args[0] = studentId;
        for (int i = 0; i < questionIds.size(); i++) {
            args[i + 1] = questionIds.get(i);
        }
        List<Long> found = jdbc.queryForList(
                "SELECT question_id FROM wrong_question WHERE student_id = ? AND delete_flag = 0 AND question_id IN ("
                        + placeholders + ")",
                Long.class, args);
        return new HashSet<>(found);
    }

    private void markWrong(long studentId, long questionId, String answer, LocalDateTime now) {
        int updated = jdbc.update("""
                UPDATE wrong_question
                SET delete_flag = 0, error_count = error_count + 1, corrected_count = 0, status = 1,
                    last_answer = ?, last_wrong_at = ?, updated_at = ?
                WHERE student_id = ? AND question_id = ?
                """, answer, now, now, studentId, questionId);
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO wrong_question
                    (student_id, question_id, error_count, corrected_count, status, last_wrong_at, last_answer, created_at, updated_at, delete_flag)
                    VALUES (?, ?, 1, 0, 1, ?, ?, ?, ?, 0)
                    """, studentId, questionId, now, answer, now, now);
        } catch (DuplicateKeyException ex) {
            jdbc.update("""
                    UPDATE wrong_question
                    SET delete_flag = 0, error_count = error_count + 1, corrected_count = 0, status = 1,
                        last_answer = ?, last_wrong_at = ?, updated_at = ?
                    WHERE student_id = ? AND question_id = ?
                    """, answer, now, now, studentId, questionId);
        }
    }

    private void markCorrect(long studentId, long questionId, LocalDateTime now) {
        jdbc.update("""
                UPDATE wrong_question
                SET delete_flag = 0,
                    status = IF(corrected_count + 1 >= 3, 2, status),
                    corrected_count = corrected_count + 1,
                    updated_at = ?
                WHERE student_id = ? AND question_id = ?
                """, now, studentId, questionId);
    }

    private Map<Long, List<QuestionOption>> loadOptions(List<PracticeQuestionRow> rows) {
        List<Long> ids = rows.stream().map(PracticeQuestionRow::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<QuestionOption> options = questionOptionMapper.selectList(Wrappers.<QuestionOption>lambdaQuery()
                .in(QuestionOption::getQuestionId, ids)
                .orderByAsc(QuestionOption::getSortOrder)
                .orderByAsc(QuestionOption::getId));
        Map<Long, List<QuestionOption>> grouped = new LinkedHashMap<>();
        for (QuestionOption option : options) {
            grouped.computeIfAbsent(option.getQuestionId(), key -> new ArrayList<>()).add(option);
        }
        return grouped;
    }

    private List<PracticeOption> optionViews(PracticeQuestionRow row, List<QuestionOption> options) {
        if ((options == null || options.isEmpty()) && row.getType() != null && row.getType() == 3) {
            return List.of(new PracticeOption("正确", "正确"), new PracticeOption("错误", "错误"));
        }
        if (options == null) {
            return List.of();
        }
        return options.stream()
                .map(option -> new PracticeOption(option.getOptionKey(), option.getOptionText()))
                .toList();
    }

    private String correctAnswer(PracticeQuestionRow row, List<QuestionOption> options) {
        List<String> keys = new ArrayList<>();
        if (options != null) {
            for (QuestionOption option : options) {
                if (option.getIsCorrect() != null && option.getIsCorrect() == 1
                        && option.getOptionKey() != null && !option.getOptionKey().isBlank()) {
                    keys.add(option.getOptionKey().trim());
                }
            }
        }
        if (!keys.isEmpty()) {
            keys.sort(String::compareTo);
            return String.join(",", keys);
        }
        return row.getAnswer() == null ? "" : row.getAnswer().trim();
    }

    private boolean matches(int type, String expected, String given) {
        if (type == 2) {
            return keySet(expected).equals(keySet(given));
        }
        return normalizeToken(expected).equals(normalizeToken(given));
    }

    private Set<String> keySet(String raw) {
        TreeSet<String> keys = new TreeSet<>();
        if (raw == null || raw.isBlank()) {
            return keys;
        }
        for (String part : raw.split("[,，、\\s]+")) {
            if (!part.isBlank()) {
                keys.add(part.trim().toUpperCase(Locale.ROOT));
            }
        }
        return keys;
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String titleOf(List<PracticeQuestionRow> rows, Long stageId, Long subjectId) {
        if (stageId != null) {
            return rows.get(0).getStageName();
        }
        if (subjectId != null) {
            return rows.get(0).getSubjectName();
        }
        return "综合练习";
    }

    private Long sameId(List<PracticeQuestionRow> rows, boolean subject) {
        Long id = subject ? rows.get(0).getSubjectId() : rows.get(0).getStageId();
        for (PracticeQuestionRow row : rows) {
            Long current = subject ? row.getSubjectId() : row.getStageId();
            if (id == null || !id.equals(current)) {
                return null;
            }
        }
        return id;
    }

    private int indexOf(Paper paper, long questionId) {
        List<PaperQuestion> questions = paper.questions();
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).id() == questionId) {
                return i;
            }
        }
        throw new BizException(ErrorCode.VALIDATION_FAILED, "题目不属于本次练习");
    }

    private Paper requirePaper(String practiceId, long studentId) {
        if (practiceId == null || !practiceId.matches("[0-9a-fA-F\\-]{36}")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "练习标识无效");
        }
        String json = redis.opsForValue().get(paperKey(practiceId));
        if (json == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "练习不存在或已过期");
        }
        try {
            Paper paper = objectMapper.readValue(json, Paper.class);
            if (paper.studentId() != studentId || paper.questions() == null || paper.questions().isEmpty()) {
                throw new BizException(ErrorCode.NOT_FOUND, "练习不存在或已过期");
            }
            return paper;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "练习不存在或已过期");
        }
    }

    private void savePaper(String practiceId, Paper paper) {
        try {
            redis.opsForValue().set(paperKey(practiceId), objectMapper.writeValueAsString(paper), PAPER_TTL);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "练习创建失败");
        }
    }

    private void refresh(String practiceId) {
        redis.expire(paperKey(practiceId), PAPER_TTL);
    }

    private String paperKey(String practiceId) {
        return "practice:paper:" + practiceId;
    }

    private String normalizeAnswer(String answer) {
        String value = answer == null ? "" : answer.trim();
        if (value.length() > ANSWER_LIMIT) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "作答不能超过 5000 字");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private int requireMode(Integer mode) {
        if (mode == null || (mode != 1 && mode != 2)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择练习模式");
        }
        return mode;
    }

    private Integer optionalScale(Integer value, int min, int max, String field) {
        if (value == null) {
            return null;
        }
        if (value < min || value > max) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, field + " 超出范围");
        }
        return value;
    }

    private long requireStudentId() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        long studentId;
        try {
            studentId = Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (student.getStatus() == null || student.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        return studentId;
    }

    private record Judged(long questionId, boolean correct, String userAnswer, Integer durationSeconds,
                          PracticeResultItem result, GradeResult grade) {
    }
}
