package com.yunshu.zhixue.modules.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.constant.AppConstants;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.ai.AnswerGrader;
import com.yunshu.zhixue.modules.ai.GradeResult;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.AnswerRequest;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.ConfigView;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.Dimension;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.FinishAnswer;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.FinishRequest;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.FinishResult;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.ImproveCard;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.InterviewStats;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.PreparedGrade;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.QuestionReview;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.QuestionView;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.RecordItem;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.ReportView;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.StartRequest;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.StartResult;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.SubjectOption;
import com.yunshu.zhixue.modules.interview.dto.InterviewPortalPayloads.Weakness;
import com.yunshu.zhixue.modules.interview.dto.InterviewRecordRow;
import com.yunshu.zhixue.modules.interview.dto.InterviewSessionState;
import com.yunshu.zhixue.modules.interview.dto.InterviewSessionState.Item;
import com.yunshu.zhixue.modules.interview.dto.InterviewStatsRow;
import com.yunshu.zhixue.modules.interview.entity.InterviewQuestionScore;
import com.yunshu.zhixue.modules.interview.entity.InterviewRecord;
import com.yunshu.zhixue.modules.interview.entity.InterviewReport;
import com.yunshu.zhixue.modules.interview.mapper.InterviewRecordMapper;
import com.yunshu.zhixue.modules.interview.mapper.InterviewReportMapper;
import com.yunshu.zhixue.modules.interview.mapper.InterviewScoreMapper;
import com.yunshu.zhixue.modules.interview.service.InterviewReportComposer.Content;
import com.yunshu.zhixue.modules.interviewbank.entity.InterviewQuestion;
import com.yunshu.zhixue.modules.interviewbank.mapper.InterviewQuestionMapper;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InterviewPortalService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int ANSWER_LIMIT = 5000;
    private static final int ANSWER_TIMEOUT_SECONDS = AppConstants.INTERVIEW_ANSWER_TIMEOUT_MINUTES * 60;
    private static final List<Integer> COUNTS = List.of(3, 5, 8, 10, 15);
    private static final Set<Integer> COUNT_SET = Set.copyOf(COUNTS);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    private static final TypeReference<List<Dimension>> DIMENSIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> TEXTS = new TypeReference<>() {
    };
    private static final TypeReference<List<Weakness>> WEAKNESSES = new TypeReference<>() {
    };
    private static final TypeReference<List<ImproveCard>> CARDS = new TypeReference<>() {
    };

    private final SubjectMapper subjectMapper;
    private final StudentMapper studentMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewRecordMapper interviewRecordMapper;
    private final InterviewScoreMapper interviewScoreMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final AnswerGrader answerGrader;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public InterviewPortalService(SubjectMapper subjectMapper, StudentMapper studentMapper,
                                  InterviewQuestionMapper interviewQuestionMapper, InterviewRecordMapper interviewRecordMapper,
                                  InterviewScoreMapper interviewScoreMapper, InterviewReportMapper interviewReportMapper,
                                  AnswerGrader answerGrader, StringRedisTemplate redis, ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.subjectMapper = subjectMapper;
        this.studentMapper = studentMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.interviewRecordMapper = interviewRecordMapper;
        this.interviewScoreMapper = interviewScoreMapper;
        this.interviewReportMapper = interviewReportMapper;
        this.answerGrader = answerGrader;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ConfigView config() {
        long studentId = requireStudentId();
        List<SubjectOption> subjects = subjectMapper.selectList(Wrappers.<Subject>lambdaQuery()
                        .orderByAsc(Subject::getSortOrder)
                        .orderByAsc(Subject::getId))
                .stream()
                .map(subject -> new SubjectOption(subject.getId(), subject.getName()))
                .toList();
        return new ConfigView(subjects, List.of(1, 2, 3), COUNTS, AppConstants.DEFAULT_INTERVIEW_QUESTION_COUNT,
                remaining(studentId), AppConstants.INTERVIEW_ANSWER_TIMEOUT_MINUTES);
    }

    public StartResult start(StartRequest request, String idempotencyKey) {
        long studentId = requireStudentId();
        String idemKey = idempotencyKey(studentId, "start", idempotencyKey);
        StartResult cached = readIdempotent(idemKey, StartResult.class);
        if (cached != null) {
            return cached;
        }
        if (request == null || request.subjectId() == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择技术方向");
        }
        int difficulty = requireDifficulty(request.difficulty());
        int count = requireCount(request.count());
        Subject subject = subjectMapper.selectById(request.subjectId());
        if (subject == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "学科不存在");
        }
        boolean charged = false;
        try {
            charge(studentId);
            charged = true;
            InterviewSessionState state = newSession(studentId, subject, difficulty, count);
            String sessionId = UUID.randomUUID().toString();
            saveSession(sessionId, state);
            StartResult result = new StartResult(sessionId, state.getQuestions().stream()
                    .map(item -> new QuestionView(item.getOrder(), item.getSubjectId(), item.getDifficulty(),
                            item.getContent(), item.getSource()))
                    .toList(), count);
            writeIdempotent(idemKey, result);
            return result;
        } catch (RuntimeException ex) {
            if (charged && !(ex instanceof BizException biz && biz.getErrorCode() == ErrorCode.INTERVIEW_QUOTA_EXHAUSTED)) {
                refund(studentId);
            }
            throw ex;
        }
    }

    public long currentStudentId() {
        return requireStudentId();
    }

    public PreparedGrade prepareAnswer(String sessionId, AnswerRequest request) {
        return prepareAnswer(requireStudentId(), sessionId, request);
    }

    public PreparedGrade prepareAnswer(long studentId, String sessionId, AnswerRequest request) {
        requireSessionId(sessionId);
        if (request == null || request.order() == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请指定题目");
        }
        String answer = normalizeAnswer(request.studentAnswer());
        int duration = clampDuration(request.durationSeconds());
        return withLockedState(studentId, sessionId, state -> {
            Item current = find(state, request.order());
            if (current == null) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "题目序号无效");
            }
            if (current.getScore() != null) {
                return toGrade(current);
            }
            int expected = nextOrder(state);
            if (current.getOrder() != expected) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "请按顺序作答");
            }
            GradeResult grade = answerGrader.grade(current.getContent(), current.getReferenceAnswer(), answer);
            current.setScore(grade.score());
            current.setFeedback(grade.feedback());
            current.setDegraded(grade.degraded());
            current.setReferencePoints(grade.referencePoints());
            current.setStudentAnswer(answer.isEmpty() ? null : answer);
            current.setDurationSeconds(duration);
            saveSession(sessionId, state);
            return toGrade(current);
        });
    }

    public FinishResult finish(String sessionId, FinishRequest request, String idempotencyKey) {
        long studentId = requireStudentId();
        requireSessionId(sessionId);
        String idemKey = idempotencyKey(studentId, "finish", idempotencyKey);
        FinishResult cached = readIdempotent(idemKey, FinishResult.class);
        if (cached != null) {
            redis.delete(tempKey(sessionId));
            return cached;
        }
        String lockKey = lockKey(sessionId);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(120)))) {
            throw new BizException(ErrorCode.CONFLICT, "报告正在生成，请稍候");
        }
        try {
            FinishResult again = readIdempotent(idemKey, FinishResult.class);
            if (again != null) {
                redis.delete(tempKey(sessionId));
                return again;
            }
            InterviewSessionState state = loadSession(sessionId, studentId);
            applyFinishAnswers(state, request);
            saveSession(sessionId, state);
            FinishResult result = transaction.execute(status -> persist(studentId, state));
            if (result == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "面试报告生成失败");
            }
            redis.delete(tempKey(sessionId));
            try {
                writeIdempotent(idemKey, result);
            } catch (RuntimeException ignored) {
                // 报告已经落库。缓存失败时，学员仍可从面试记录打开。
            }
            return result;
        } finally {
            redis.delete(lockKey);
        }
    }

    public ReportView report(long reportId) {
        long studentId = requireStudentId();
        return toReport(requireOwnedReport(studentId, reportId));
    }

    @Transactional
    public ReportView regenerate(long reportId) {
        long studentId = requireStudentId();
        Owned owned = requireOwnedReport(studentId, reportId);
        List<InterviewQuestionScore> scores = scoresOf(owned.record().getId());
        Content content = InterviewReportComposer.compose(owned.subjectName(), owned.record().getDifficulty(),
                itemsFromScores(scores, owned.record().getDifficulty()));
        InterviewReport report = owned.report();
        report.setRadarJson(writeJson(content.dimensions()));
        report.setStrengths(writeJson(content.strengths()));
        report.setWeaknesses(writeJson(content.weaknesses()));
        report.setSuggestions(writeJson(content.suggestions()));
        report.setImprovementCardsJson(writeJson(content.cards()));
        interviewReportMapper.updateById(report);
        return toReport(new Owned(owned.record(), report, owned.subjectName(), scores));
    }

    public PageResult<RecordItem> records(long page, long pageSize) {
        long studentId = requireStudentId();
        long total = interviewRecordMapper.countByStudent(studentId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, page, pageSize);
        }
        List<RecordItem> list = interviewRecordMapper.pageByStudent(studentId, pageSize, (page - 1) * pageSize).stream()
                .map(row -> new RecordItem(
                        row.getId(),
                        row.getSubjectId() == null ? 0 : row.getSubjectId(),
                        row.getSubjectName(),
                        row.getDifficulty() == null ? 2 : row.getDifficulty(),
                        row.getQuestionCount() == null ? 0 : row.getQuestionCount(),
                        scaleScore(row.getOverallScore()),
                        row.getTotalDuration() == null ? 0 : row.getTotalDuration(),
                        row.getCompletedAt(),
                        row.getReportId() != null))
                .toList();
        return new PageResult<>(list, total, page, pageSize);
    }

    public InterviewStats stats() {
        long studentId = requireStudentId();
        InterviewStatsRow row = interviewRecordMapper.stats(studentId);
        if (row == null || row.getInterviewTotal() == null || row.getInterviewTotal() == 0) {
            return new InterviewStats(0, ZERO, ZERO, 0);
        }
        return new InterviewStats(row.getInterviewTotal(), scaleScore(row.getAvgScore()), scaleScore(row.getMaxScore()),
                row.getTotalDuration() == null ? 0 : row.getTotalDuration());
    }

    private FinishResult persist(long studentId, InterviewSessionState state) {
        List<Item> questions = state.getQuestions();
        BigDecimal overall = average(questions);
        int duration = questions.stream().mapToInt(item -> item.getDurationSeconds() == null ? 0 : item.getDurationSeconds()).sum();
        LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC);
        InterviewRecord record = new InterviewRecord();
        record.setStudentId(studentId);
        record.setSubjectId(state.getSubjectId());
        record.setDifficulty(state.getDifficulty());
        record.setQuestionCount(questions.size());
        record.setOverallScore(overall);
        record.setTotalDuration(duration);
        record.setStartedAt(state.getStartedAt() == null ? completedAt : state.getStartedAt());
        record.setCompletedAt(completedAt);
        interviewRecordMapper.insert(record);
        for (Item item : questions) {
            InterviewQuestionScore score = new InterviewQuestionScore();
            score.setInterviewRecordId(record.getId());
            score.setQuestionOrder(item.getOrder());
            score.setQuestionId(item.getQuestionId());
            score.setQuestionText(item.getContent() == null ? "" : item.getContent());
            score.setStudentAnswer(blankToNull(item.getStudentAnswer()));
            score.setScore(BigDecimal.valueOf(item.getScore() == null ? 0 : item.getScore()).setScale(1, RoundingMode.HALF_UP));
            score.setAiFeedback(blankToNull(item.getFeedback()));
            interviewScoreMapper.insert(score);
        }
        Content content = InterviewReportComposer.compose(state.getSubjectName(), state.getDifficulty(), questions);
        InterviewReport report = new InterviewReport();
        report.setInterviewRecordId(record.getId());
        report.setRadarJson(writeJson(content.dimensions()));
        report.setStrengths(writeJson(content.strengths()));
        report.setWeaknesses(writeJson(content.weaknesses()));
        report.setSuggestions(writeJson(content.suggestions()));
        report.setImprovementCardsJson(writeJson(content.cards()));
        interviewReportMapper.insert(report);
        return new FinishResult(report.getId(), overall);
    }

    private void applyFinishAnswers(InterviewSessionState state, FinishRequest request) {
        if (request != null && request.answers() != null) {
            if (request.answers().size() > COUNTS.get(COUNTS.size() - 1)) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "作答数量超出本次面试题数");
            }
            for (FinishAnswer input : request.answers()) {
                if (input == null || input.order() == null) {
                    throw new BizException(ErrorCode.VALIDATION_FAILED, "请指定题目");
                }
                Item item = find(state, input.order());
                if (item == null) {
                    throw new BizException(ErrorCode.VALIDATION_FAILED, "题目序号无效");
                }
                if (item.getScore() == null) {
                    String answer = normalizeAnswer(input.studentAnswer());
                    if (!answer.isEmpty()) {
                        GradeResult grade = answerGrader.grade(item.getContent(), item.getReferenceAnswer(), answer);
                        item.setScore(grade.score());
                        item.setFeedback(grade.feedback());
                        item.setDegraded(grade.degraded());
                        item.setReferencePoints(grade.referencePoints());
                        item.setStudentAnswer(answer);
                    }
                }
                if (input.durationSeconds() != null) {
                    item.setDurationSeconds(clampDuration(input.durationSeconds()));
                }
            }
        }
        for (Item item : state.getQuestions()) {
            if (item.getScore() == null) {
                item.setScore(0);
                item.setFeedback("未作答");
                item.setDegraded(true);
                item.setStudentAnswer(null);
                item.setReferencePoints(List.of());
            }
            if (item.getDurationSeconds() == null) {
                item.setDurationSeconds(0);
            }
        }
    }

    private InterviewSessionState newSession(long studentId, Subject subject, int difficulty, int count) {
        List<InterviewQuestion> bank = interviewQuestionMapper.drawPublished(subject.getId(), difficulty, count);
        List<Item> questions = new ArrayList<>();
        if (bank != null) {
            for (InterviewQuestion row : bank) {
                if (row.getContent() == null || row.getContent().isBlank()) {
                    continue;
                }
                Item item = new Item();
                item.setQuestionId(row.getId());
                item.setSubjectId(subject.getId());
                item.setDifficulty(difficulty);
                item.setContent(row.getContent().trim());
                item.setReferenceAnswer(row.getReferenceAnswer() == null ? "" : row.getReferenceAnswer());
                item.setSource("bank");
                questions.add(item);
                if (questions.size() == count) {
                    break;
                }
            }
        }
        int serial = questions.size();
        while (questions.size() < count) {
            questions.add(InterviewSnapshots.create(subject, difficulty, questions.size() + 1, serial));
            serial++;
        }
        if (questions.size() < count) {
            throw new BizException(ErrorCode.INTERVIEW_QUESTION_SHORTAGE);
        }
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setOrder(i + 1);
        }
        InterviewSessionState state = new InterviewSessionState();
        state.setStudentId(studentId);
        state.setSubjectId(subject.getId());
        state.setSubjectName(subject.getName());
        state.setDifficulty(difficulty);
        state.setQuestionCount(count);
        state.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        state.setQuestions(questions);
        return state;
    }

    private ReportView toReport(Owned owned) {
        InterviewRecord record = owned.record();
        BigDecimal overall = scaleScore(record.getOverallScore());
        BigDecimal previous = interviewRecordMapper.previousScore(record.getStudentId(), record.getCompletedAt(), record.getId());
        BigDecimal trend = previous == null ? null : overall.subtract(scaleScore(previous)).setScale(1, RoundingMode.HALF_UP);
        List<InterviewQuestionScore> scores = owned.scores();
        long ai = scores.stream().filter(item -> item.getQuestionId() == null).count();
        BigDecimal coverage = scores.isEmpty()
                ? ZERO
                : BigDecimal.valueOf(ai).divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        int accuracy = overall.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValue();
        return new ReportView(
                owned.report().getId(),
                record.getId(),
                record.getSubjectId(),
                owned.subjectName(),
                record.getDifficulty() == null ? 2 : record.getDifficulty(),
                record.getQuestionCount() == null ? scores.size() : record.getQuestionCount(),
                record.getTotalDuration() == null ? 0 : record.getTotalDuration(),
                record.getCompletedAt(),
                overall,
                accuracy,
                trend,
                coverage,
                readJson(owned.report().getRadarJson(), DIMENSIONS, List.of()),
                readJson(owned.report().getStrengths(), TEXTS, List.of()),
                readJson(owned.report().getWeaknesses(), WEAKNESSES, List.of()),
                readJson(owned.report().getSuggestions(), TEXTS, List.of()),
                readJson(owned.report().getImprovementCardsJson(), CARDS, List.of()),
                scores.stream()
                        .map(item -> new QuestionReview(
                                item.getQuestionOrder() == null ? 0 : item.getQuestionOrder(),
                                item.getQuestionText(),
                                item.getStudentAnswer(),
                                scaleScore(item.getScore()),
                                item.getAiFeedback()))
                        .toList());
    }

    private Owned requireOwnedReport(long studentId, long reportId) {
        InterviewReport report = interviewReportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "面试报告不存在");
        }
        InterviewRecord record = interviewRecordMapper.selectById(report.getInterviewRecordId());
        if (record == null || record.getStudentId() == null || record.getStudentId() != studentId) {
            throw new BizException(ErrorCode.NOT_FOUND, "面试报告不存在");
        }
        Subject subject = subjectMapper.selectById(record.getSubjectId());
        String subjectName = subject == null || subject.getName() == null ? "未知方向" : subject.getName();
        return new Owned(record, report, subjectName, scoresOf(record.getId()));
    }

    private List<InterviewQuestionScore> scoresOf(long recordId) {
        return interviewScoreMapper.selectList(Wrappers.<InterviewQuestionScore>lambdaQuery()
                .eq(InterviewQuestionScore::getInterviewRecordId, recordId)
                .orderByAsc(InterviewQuestionScore::getQuestionOrder)
                .orderByAsc(InterviewQuestionScore::getId));
    }

    private List<Item> itemsFromScores(List<InterviewQuestionScore> scores, int difficulty) {
        List<Item> items = new ArrayList<>();
        for (InterviewQuestionScore score : scores) {
            Item item = new Item();
            item.setOrder(score.getQuestionOrder() == null ? items.size() + 1 : score.getQuestionOrder());
            item.setDifficulty(difficulty);
            item.setContent(score.getQuestionText());
            item.setStudentAnswer(score.getStudentAnswer());
            item.setFeedback(score.getAiFeedback());
            item.setScore(score.getScore() == null ? 0 : score.getScore().setScale(0, RoundingMode.HALF_UP).intValue());
            items.add(item);
        }
        return items;
    }

    private <T> T withLockedState(long studentId, String sessionId, java.util.function.Function<InterviewSessionState, T> action) {
        String lockKey = lockKey(sessionId);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(120)))) {
            throw new BizException(ErrorCode.CONFLICT, "正在处理上一题，请稍候");
        }
        try {
            InterviewSessionState state = loadSession(sessionId, studentId);
            return action.apply(state);
        } finally {
            redis.delete(lockKey);
        }
    }

    private InterviewSessionState loadSession(String sessionId, long studentId) {
        String json = redis.opsForValue().get(tempKey(sessionId));
        if (json == null || json.isBlank()) {
            throw new BizException(ErrorCode.INTERVIEW_SESSION_EXPIRED);
        }
        InterviewSessionState state;
        try {
            state = objectMapper.readValue(json, InterviewSessionState.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERVIEW_SESSION_EXPIRED);
        }
        if (state == null || state.getStudentId() != studentId || state.getQuestions() == null || state.getQuestions().isEmpty()) {
            throw new BizException(ErrorCode.INTERVIEW_SESSION_EXPIRED);
        }
        return state;
    }

    private void saveSession(String sessionId, InterviewSessionState state) {
        try {
            redis.opsForValue().set(tempKey(sessionId), objectMapper.writeValueAsString(state), SESSION_TTL);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "面试会话保存失败");
        }
    }

    private void charge(long studentId) {
        String key = quotaKey(studentId);
        Long used = redis.opsForValue().increment(key);
        Long ttl = redis.getExpire(key);
        if (ttl == null || ttl < 0) {
            redis.expire(key, untilNextShanghaiMidnight());
        }
        if (used != null && used > AppConstants.INTERVIEW_DAILY_QUOTA) {
            redis.opsForValue().decrement(key);
            throw new BizException(ErrorCode.INTERVIEW_QUOTA_EXHAUSTED);
        }
    }

    private void refund(long studentId) {
        String key = quotaKey(studentId);
        Long used = redis.opsForValue().decrement(key);
        if (used != null && used < 0) {
            redis.opsForValue().set(key, "0");
            redis.expire(key, untilNextShanghaiMidnight());
        }
    }

    private int remaining(long studentId) {
        String raw = redis.opsForValue().get(quotaKey(studentId));
        int used = 0;
        if (raw != null) {
            try {
                used = Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                used = AppConstants.INTERVIEW_DAILY_QUOTA;
            }
        }
        return Math.max(0, AppConstants.INTERVIEW_DAILY_QUOTA - used);
    }

    private String quotaKey(long studentId) {
        String day = ZonedDateTime.now(SHANGHAI).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "interview:quota:" + studentId + ":" + day;
    }

    private String tempKey(String sessionId) {
        return "interview:temp:" + sessionId;
    }

    private String lockKey(String sessionId) {
        return "interview:lock:" + sessionId;
    }

    private Duration untilNextShanghaiMidnight() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI);
        Duration duration = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(SHANGHAI));
        return duration.isZero() || duration.isNegative() ? Duration.ofSeconds(1) : duration;
    }

    private int requireDifficulty(Integer difficulty) {
        if (difficulty == null || difficulty < 1 || difficulty > 3) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择面试难度");
        }
        return difficulty;
    }

    private int requireCount(Integer count) {
        if (count == null || !COUNT_SET.contains(count)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择题目数量");
        }
        return count;
    }

    private void requireSessionId(String sessionId) {
        if (sessionId == null || !sessionId.matches("[0-9a-fA-F-]{32,36}")) {
            throw new BizException(ErrorCode.INTERVIEW_SESSION_EXPIRED);
        }
    }

    private String normalizeAnswer(String answer) {
        String text = answer == null ? "" : answer.trim();
        if (text.length() > ANSWER_LIMIT) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "回答不能超过 5000 字");
        }
        return text;
    }

    private int clampDuration(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds < 0) {
            return 0;
        }
        return Math.min(durationSeconds, ANSWER_TIMEOUT_SECONDS);
    }

    private Item find(InterviewSessionState state, int order) {
        for (Item item : state.getQuestions()) {
            if (item.getOrder() == order) {
                return item;
            }
        }
        return null;
    }

    private int nextOrder(InterviewSessionState state) {
        return state.getQuestions().stream()
                .filter(item -> item.getScore() == null)
                .mapToInt(Item::getOrder)
                .min()
                .orElse(0);
    }

    private PreparedGrade toGrade(Item item) {
        List<String> points = item.getReferencePoints() == null ? List.of() : item.getReferencePoints();
        return new PreparedGrade(item.getOrder(), item.getScore() == null ? 0 : item.getScore(),
                item.getFeedback() == null ? "" : item.getFeedback(), Boolean.TRUE.equals(item.getDegraded()), points);
    }

    private BigDecimal average(List<Item> questions) {
        if (questions.isEmpty()) {
            return ZERO;
        }
        int sum = questions.stream().mapToInt(item -> item.getScore() == null ? 0 : item.getScore()).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(questions.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleScore(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String idempotencyKey(long studentId, String action, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (!trimmed.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 无效");
        }
        return "idempotency:interview:" + action + ":" + studentId + ":" + trimmed;
    }

    private <T> T readIdempotent(String key, Class<T> type) {
        if (key == null) {
            return null;
        }
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void writeIdempotent(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), IDEMPOTENCY_TTL);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "请求结果保存失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "面试报告生成失败");
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
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

    private record Owned(InterviewRecord record, InterviewReport report, String subjectName, List<InterviewQuestionScore> scores) {
    }
}
