package com.yunshu.zhixue.modules.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.enums.QuestionStatus;
import com.yunshu.zhixue.common.enums.QuestionType;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.AuditRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.BatchIdsRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.BatchResult;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.BatchStatusRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.ImportConfirmResult;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.ImportPreview;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.ImportRow;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.OptionInput;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.OptionView;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionDetail;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionItem;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionSaveRequest;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.QuestionStats;
import com.yunshu.zhixue.modules.question.dto.QuestionPayloads.StageOption;
import com.yunshu.zhixue.modules.question.dto.QuestionRow;
import com.yunshu.zhixue.modules.question.entity.Question;
import com.yunshu.zhixue.modules.question.entity.QuestionOption;
import com.yunshu.zhixue.modules.question.mapper.QuestionMapper;
import com.yunshu.zhixue.modules.question.mapper.QuestionOptionMapper;
import com.yunshu.zhixue.modules.question.service.QuestionWorkbook.ParsedQuestion;
import com.yunshu.zhixue.modules.subject.entity.Stage;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.StageMapper;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class QuestionAdminService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);
    private static final int EXPORT_LIMIT = 10_000;
    private static final TypeReference<List<QuestionDraft>> DRAFTS = new TypeReference<>() {
    };
    private static final DefaultRedisScript<String> TAKE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
              redis.call('DEL', KEYS[1])
            end
            return value
            """, String.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final StageMapper stageMapper;
    private final SubjectMapper subjectMapper;
    private final QuestionWorkbook workbook;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OperationLogWriter operationLogWriter;

    public QuestionAdminService(QuestionMapper questionMapper, QuestionOptionMapper optionMapper, StageMapper stageMapper,
                                SubjectMapper subjectMapper, QuestionWorkbook workbook, JdbcTemplate jdbcTemplate,
                                StringRedisTemplate redis, ObjectMapper objectMapper, OperationLogWriter operationLogWriter) {
        this.questionMapper = questionMapper;
        this.optionMapper = optionMapper;
        this.stageMapper = stageMapper;
        this.subjectMapper = subjectMapper;
        this.workbook = workbook;
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.operationLogWriter = operationLogWriter;
    }

    public QuestionStats stats() {
        return new QuestionStats(
                questionMapper.countAll(),
                questionMapper.countByStatus(QuestionStatus.PUBLISHED.code()),
                questionMapper.countByStatus(QuestionStatus.PENDING.code()),
                questionMapper.countByType(QuestionType.SINGLE.code()),
                questionMapper.countByType(QuestionType.MULTIPLE.code()),
                questionMapper.countByType(QuestionType.JUDGE.code()),
                questionMapper.countByType(QuestionType.SHORT_ANSWER.code()));
    }

    public PageResult<QuestionItem> page(String keyword, Long subjectId, Long stageId, Integer type, Integer difficulty,
                                         Integer status, long page, long pageSize) {
        validateFilter(type, difficulty, status);
        Page<QuestionRow> result = questionMapper.search(new Page<>(page, pageSize), like(keyword), subjectId, stageId, type, difficulty, status);
        List<QuestionItem> list = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    public QuestionDetail detail(Long id) {
        QuestionRow row = requireRow(id);
        List<OptionView> options = optionsOf(id).stream()
                .map(option -> new OptionView(option.getOptionKey(), option.getOptionText(), option.getIsCorrect() != null && option.getIsCorrect() == 1))
                .toList();
        long done = row.getDoneCount() == null ? 0 : row.getDoneCount();
        long correct = row.getCorrectCount() == null ? 0 : row.getCorrectCount();
        return new QuestionDetail(row.getId(), row.getContent(), row.getStageId(), row.getStageName(), row.getSubjectName(),
                row.getType(), row.getDifficulty(), row.getAnswer(), row.getAnalysis(), row.getTags(), row.getStatus(),
                row.getRejectReason(), formatTime(row.getCreatedAt()), options, done, correct, accuracy(done, correct));
    }

    public List<StageOption> stages() {
        Map<Long, String> subjects = new LinkedHashMap<>();
        for (Subject subject : subjectMapper.selectList(new LambdaQueryWrapper<Subject>().orderByAsc(Subject::getSortOrder).orderByAsc(Subject::getId))) {
            subjects.put(subject.getId(), subject.getName());
        }
        List<StageOption> options = new ArrayList<>();
        List<Stage> stages = stageMapper.selectList(new LambdaQueryWrapper<Stage>().orderByAsc(Stage::getSubjectId).orderByAsc(Stage::getSortOrder).orderByAsc(Stage::getId));
        for (Stage stage : stages) {
            String subjectName = subjects.get(stage.getSubjectId());
            if (subjectName == null) {
                continue;
            }
            options.add(new StageOption(stage.getId(), stage.getName(), stage.getSubjectId(), subjectName));
        }
        return options;
    }

    @Transactional
    public void create(QuestionSaveRequest request) {
        Prepared prepared = prepare(request);
        Question question = new Question();
        question.setStageId(prepared.stageId());
        question.setType(prepared.type());
        question.setDifficulty(prepared.difficulty());
        question.setContent(prepared.content());
        question.setAnswer(prepared.answer());
        question.setAnalysis(prepared.analysis());
        question.setTags(prepared.tags());
        question.setStatus(QuestionStatus.PENDING.code());
        questionMapper.insert(question);
        replaceOptions(question.getId(), prepared.type(), prepared.options());
        operationLogWriter.write("新增", "题目", label(prepared.content()));
    }

    @Transactional
    public void update(Long id, QuestionSaveRequest request) {
        Question current = requireQuestion(id);
        Prepared prepared = prepare(request);
        int status = current.getStatus() == null ? QuestionStatus.PENDING.code() : current.getStatus();
        String rejectReason = current.getRejectReason();
        if (status == QuestionStatus.REJECTED.code() || status == QuestionStatus.DRAFT.code()) {
            status = QuestionStatus.PENDING.code();
            rejectReason = null;
        }
        writeQuestion(id, prepared, status, rejectReason);
        replaceOptions(id, prepared.type(), prepared.options());
        operationLogWriter.write("修改", "题目", label(prepared.content()));
    }

    @Transactional
    public void delete(Long id) {
        Question question = requireQuestion(id);
        if (referenced(id)) {
            throw new BizException(ErrorCode.HAS_RELATIONS, "题目已被练习或错题引用，不能删除");
        }
        jdbcTemplate.update("DELETE FROM question_option WHERE question_id = ?", id);
        questionMapper.deleteById(id);
        operationLogWriter.write("删除", "题目", label(question.getContent()));
    }

    @Transactional
    public void audit(Long id, AuditRequest request) {
        Question question = requireQuestion(id);
        if (question.getStatus() == null || question.getStatus() != QuestionStatus.PENDING.code()) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS, "只有待审核的题目可以审核");
        }
        if (Boolean.TRUE.equals(request.pass())) {
            jdbcTemplate.update("UPDATE question SET status = ?, reject_reason = NULL, updated_at = ? WHERE id = ? AND delete_flag = 0",
                    QuestionStatus.PUBLISHED.code(), LocalDateTime.now(ZoneOffset.UTC), id);
        } else {
            String reason = request.rejectReason() == null ? "" : request.rejectReason().trim();
            if (reason.isEmpty() || reason.length() > 255) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写 1~255 字的驳回原因");
            }
            jdbcTemplate.update("UPDATE question SET status = ?, reject_reason = ?, updated_at = ? WHERE id = ? AND delete_flag = 0",
                    QuestionStatus.REJECTED.code(), reason, LocalDateTime.now(ZoneOffset.UTC), id);
        }
        operationLogWriter.write("修改", "题目", label(question.getContent()));
    }

    @Transactional
    public void changeStatus(Long id, String action) {
        Question question = requireQuestion(id);
        applyStatus(question, action);
        operationLogWriter.write("修改", "题目", label(question.getContent()));
    }

    @Transactional
    public void resubmit(Long id) {
        Question question = requireQuestion(id);
        if (question.getStatus() == null || question.getStatus() != QuestionStatus.REJECTED.code()) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS, "只有已驳回的题目可以重新提交");
        }
        jdbcTemplate.update("UPDATE question SET status = ?, reject_reason = NULL, updated_at = ? WHERE id = ? AND delete_flag = 0",
                QuestionStatus.PENDING.code(), LocalDateTime.now(ZoneOffset.UTC), id);
        operationLogWriter.write("修改", "题目", label(question.getContent()));
    }

    @Transactional
    public BatchResult batchStatus(BatchStatusRequest request) {
        int affected = 0;
        int skipped = 0;
        for (Long id : request.ids().stream().distinct().toList()) {
            Question question = questionMapper.selectById(id);
            if (question == null) {
                skipped++;
                continue;
            }
            try {
                applyStatus(question, request.action());
                affected++;
            } catch (BizException ex) {
                skipped++;
            }
        }
        if (affected > 0) {
            operationLogWriter.write("修改", "题目", "批量" + ("off".equals(normalizeAction(request.action())) ? "下架" : "上架") + affected + "道");
        }
        return new BatchResult(affected, skipped);
    }

    @Transactional
    public BatchResult batchDelete(BatchIdsRequest request) {
        int affected = 0;
        int skipped = 0;
        for (Long id : request.ids().stream().distinct().toList()) {
            Question question = questionMapper.selectById(id);
            if (question == null || referenced(id)) {
                skipped++;
                continue;
            }
            jdbcTemplate.update("DELETE FROM question_option WHERE question_id = ?", id);
            questionMapper.deleteById(id);
            affected++;
        }
        if (affected > 0) {
            operationLogWriter.write("删除", "题目", "批量删除" + affected + "道");
        }
        return new BatchResult(affected, skipped);
    }

    @Transactional
    public BatchResult batchPass(BatchIdsRequest request) {
        int affected = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (Long id : request.ids().stream().distinct().toList()) {
            Question question = questionMapper.selectById(id);
            if (question == null || question.getStatus() == null || question.getStatus() != QuestionStatus.PENDING.code()) {
                skipped++;
                continue;
            }
            jdbcTemplate.update("UPDATE question SET status = ?, reject_reason = NULL, updated_at = ? WHERE id = ? AND delete_flag = 0",
                    QuestionStatus.PUBLISHED.code(), now, id);
            affected++;
        }
        if (affected > 0) {
            operationLogWriter.write("修改", "题目", "批量通过" + affected + "道");
        }
        return new BatchResult(affected, skipped);
    }

    public byte[] template() {
        return workbook.template();
    }

    public ImportPreview preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_FILE, "请上传 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw new BizException(ErrorCode.BAD_FILE, "请上传 .xlsx 或 .xls 文件");
        }
        List<ParsedQuestion> parsed;
        try (var input = file.getInputStream()) {
            parsed = workbook.parse(input);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "无法读取 Excel 文件，请使用模板重新填写");
        }
        Map<String, List<Stage>> stages = stagesByName();
        List<ImportRow> rows = new ArrayList<>();
        List<QuestionDraft> drafts = new ArrayList<>();
        int valid = 0;
        for (ParsedQuestion item : parsed) {
            List<String> messages = new ArrayList<>(item.messages());
            Long stageId = null;
            if (messages.isEmpty()) {
                List<Stage> matched = stages.getOrDefault(item.stage(), List.of());
                if (matched.isEmpty()) {
                    messages.add("阶段「" + item.stage() + "」不存在，请先在学科管理中创建");
                } else if (matched.size() > 1) {
                    messages.add("阶段「" + item.stage() + "」在多个学科中存在，无法确定");
                } else {
                    stageId = matched.get(0).getId();
                }
            }
            boolean ok = messages.isEmpty() && stageId != null && item.type() != null && item.difficulty() != null;
            if (ok) {
                valid++;
                drafts.add(new QuestionDraft(stageId, item.type(), item.difficulty(), item.content(), item.answer(),
                        item.analysis(), item.tags(), item.options()));
            }
            rows.add(new ImportRow(item.sheet(), item.row(), item.content(), typeName(item.type()), item.stage(),
                    ok ? "ok" : "error", messages));
        }
        String token = null;
        if (!drafts.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "");
            redis.opsForValue().set(redisKey(token), writeJson(drafts), PREVIEW_TTL);
        }
        return new ImportPreview(token, rows.size(), valid, rows.size() - valid, rows);
    }

    @Transactional
    public ImportConfirmResult confirm(String token) {
        if (token == null || !token.matches("[0-9a-fA-F]{32}")) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入预览已失效，请重新上传");
        }
        String json = redis.execute(TAKE_SCRIPT, List.of(redisKey(token)));
        if (json == null || json.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入预览已失效，请重新上传");
        }
        List<QuestionDraft> drafts = readDrafts(json);
        if (drafts.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "没有可导入的题目");
        }
        for (QuestionDraft draft : drafts) {
            Question question = new Question();
            question.setStageId(draft.stageId());
            question.setType(draft.type());
            question.setDifficulty(draft.difficulty());
            question.setContent(draft.content());
            question.setAnswer(blankToNull(draft.answer()));
            question.setAnalysis(blankToNull(draft.analysis()));
            question.setTags(blankToNull(draft.tags()));
            question.setStatus(QuestionStatus.PENDING.code());
            questionMapper.insert(question);
            replaceOptions(question.getId(), draft.type(), draft.options() == null ? List.of() : draft.options());
        }
        operationLogWriter.write("新增", "题目", "导入" + drafts.size() + "道");
        return new ImportConfirmResult(drafts.size());
    }

    public byte[] export(String keyword, Long subjectId, Long stageId, Integer type, Integer difficulty, Integer status) {
        validateFilter(type, difficulty, status);
        String like = like(keyword);
        long total = questionMapper.countFiltered(like, subjectId, stageId, type, difficulty, status);
        if (total > EXPORT_LIMIT) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "导出结果超过 1 万行，请缩小筛选范围");
        }
        List<QuestionRow> rows = total == 0
                ? List.of()
                : questionMapper.search(new Page<>(1, total, false), like, subjectId, stageId, type, difficulty, status).getRecords();
        Map<Long, List<QuestionOption>> options = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(QuestionRow::getId).toList();
            List<QuestionOption> found = optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                    .in(QuestionOption::getQuestionId, ids)
                    .orderByAsc(QuestionOption::getSortOrder)
                    .orderByAsc(QuestionOption::getId));
            for (QuestionOption option : found) {
                options.computeIfAbsent(option.getQuestionId(), key -> new ArrayList<>()).add(option);
            }
        }
        List<String[]> lines = new ArrayList<>();
        for (QuestionRow row : rows) {
            lines.add(exportLine(row, options.getOrDefault(row.getId(), List.of())));
        }
        operationLogWriter.write("导出", "题目", "导出" + lines.size() + "道");
        return workbook.export(lines);
    }

    private void applyStatus(Question question, String action) {
        String normalized = normalizeAction(action);
        int status = question.getStatus() == null ? 0 : question.getStatus();
        if ("off".equals(normalized)) {
            if (status != QuestionStatus.PUBLISHED.code()) {
                throw new BizException(ErrorCode.ILLEGAL_STATUS, "只有已上架的题目可以下架");
            }
            jdbcTemplate.update("UPDATE question SET status = ?, updated_at = ? WHERE id = ? AND delete_flag = 0",
                    QuestionStatus.UNPUBLISHED.code(), LocalDateTime.now(ZoneOffset.UTC), question.getId());
            return;
        }
        if ("on".equals(normalized)) {
            if (status != QuestionStatus.UNPUBLISHED.code()) {
                throw new BizException(ErrorCode.ILLEGAL_STATUS, "只有已下架的题目可以重新上架");
            }
            jdbcTemplate.update("UPDATE question SET status = ?, reject_reason = NULL, updated_at = ? WHERE id = ? AND delete_flag = 0",
                    QuestionStatus.PENDING.code(), LocalDateTime.now(ZoneOffset.UTC), question.getId());
            return;
        }
        throw new BizException(ErrorCode.VALIDATION_FAILED, "操作只能是上架或下架");
    }

    private static String normalizeAction(String action) {
        if (action == null) {
            return "";
        }
        String value = action.trim().toLowerCase(Locale.ROOT);
        if ("on".equals(value) || "上架".equals(action.trim())) {
            return "on";
        }
        if ("off".equals(value) || "下架".equals(action.trim())) {
            return "off";
        }
        return value;
    }

    private Prepared prepare(QuestionSaveRequest request) {
        Stage stage = stageMapper.selectById(request.stageId());
        if (stage == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "阶段不存在");
        }
        int type = request.type();
        if (type < 1 || type > 4) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "题型不正确");
        }
        int difficulty = request.difficulty();
        if (difficulty < 1 || difficulty > 3) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "难度不正确");
        }
        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写题干");
        }
        String analysis = blankToNull(request.analysis());
        String tags = blankToNull(request.tags());
        if (tags != null) {
            tags = tags.replace('，', ',').replace('、', ',');
        }
        if (type == QuestionType.JUDGE.code()) {
            String answer = blankToNull(request.answer());
            if (!"正确".equals(answer) && !"错误".equals(answer)) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "判断题答案请选择「正确」或「错误」");
            }
            return new Prepared(stage.getId(), type, difficulty, content, answer, analysis, tags, List.of());
        }
        if (type == QuestionType.SHORT_ANSWER.code()) {
            String answer = blankToNull(request.answer());
            if (answer == null) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "请填写参考答案");
            }
            return new Prepared(stage.getId(), type, difficulty, content, answer, analysis, tags, List.of());
        }
        List<OptionInput> options = normalizeOptions(type, request.options());
        return new Prepared(stage.getId(), type, difficulty, content, null, analysis, tags, options);
    }

    private List<OptionInput> normalizeOptions(int type, List<OptionInput> raw) {
        if (raw == null || raw.size() < 2 || raw.size() > 6) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "选择题需要 2 到 6 个选项");
        }
        String[] expected = {"A", "B", "C", "D", "E", "F"};
        List<OptionInput> options = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < raw.size(); i++) {
            OptionInput option = raw.get(i);
            String key = option.key() == null ? "" : option.key().trim().toUpperCase(Locale.ROOT);
            String text = option.text() == null ? "" : option.text().trim();
            if (!expected[i].equals(key)) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "选项须从 A 连续填写");
            }
            if (text.isEmpty() || text.length() > 500) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "选项" + key + "不能为空，且不超过 500 字");
            }
            boolean marked = Boolean.TRUE.equals(option.correct());
            if (marked) {
                correct++;
            }
            options.add(new OptionInput(key, text, marked));
        }
        if (type == QuestionType.SINGLE.code() && correct != 1) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "单选题只能有一个正确答案");
        }
        if (type == QuestionType.MULTIPLE.code() && correct < 1) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "多选题至少有一个正确答案");
        }
        return options;
    }

    private void writeQuestion(Long id, Prepared prepared, int status, String rejectReason) {
        int updated = jdbcTemplate.update("""
                UPDATE question
                SET stage_id = ?, type = ?, difficulty = ?, content = ?, answer = ?, analysis = ?, tags = ?,
                    status = ?, reject_reason = ?, updated_at = ?
                WHERE id = ? AND delete_flag = 0
                """, prepared.stageId(), prepared.type(), prepared.difficulty(), prepared.content(), prepared.answer(),
                prepared.analysis(), prepared.tags(), status, rejectReason, LocalDateTime.now(ZoneOffset.UTC), id);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
    }

    private void replaceOptions(Long questionId, int type, List<OptionInput> options) {
        jdbcTemplate.update("DELETE FROM question_option WHERE question_id = ?", questionId);
        if (type != QuestionType.SINGLE.code() && type != QuestionType.MULTIPLE.code()) {
            return;
        }
        int order = 1;
        for (OptionInput option : options) {
            QuestionOption row = new QuestionOption();
            row.setQuestionId(questionId);
            row.setOptionKey(option.key());
            row.setOptionText(option.text());
            row.setIsCorrect(Boolean.TRUE.equals(option.correct()) ? 1 : 0);
            row.setSortOrder(order++);
            optionMapper.insert(row);
        }
    }

    private boolean referenced(Long id) {
        return questionMapper.countPractice(id) > 0 || questionMapper.countWrong(id) > 0;
    }

    private Question requireQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    private QuestionRow requireRow(Long id) {
        return loadRow(requireQuestion(id));
    }

    private QuestionRow loadRow(Question question) {
        Stage stage = stageMapper.selectById(question.getStageId());
        Subject subject = stage == null ? null : subjectMapper.selectById(stage.getSubjectId());
        QuestionRow row = new QuestionRow();
        row.setId(question.getId());
        row.setStageId(question.getStageId());
        row.setStageName(stage == null ? "" : stage.getName());
        row.setSubjectId(subject == null ? null : subject.getId());
        row.setSubjectName(subject == null ? "" : subject.getName());
        row.setType(question.getType());
        row.setDifficulty(question.getDifficulty());
        row.setContent(question.getContent());
        row.setAnswer(question.getAnswer());
        row.setAnalysis(question.getAnalysis());
        row.setTags(question.getTags());
        row.setStatus(question.getStatus());
        row.setRejectReason(question.getRejectReason());
        row.setCreatedAt(question.getCreatedAt());
        row.setDoneCount(questionMapper.countPractice(question.getId()));
        row.setCorrectCount(questionMapper.countCorrect(question.getId()));
        return row;
    }

    private List<QuestionOption> optionsOf(Long questionId) {
        return optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSortOrder)
                .orderByAsc(QuestionOption::getId));
    }

    private QuestionItem toItem(QuestionRow row) {
        long done = row.getDoneCount() == null ? 0 : row.getDoneCount();
        long correct = row.getCorrectCount() == null ? 0 : row.getCorrectCount();
        return new QuestionItem(row.getId(), row.getContent(), row.getStageId(), nullToEmpty(row.getStageName()),
                nullToEmpty(row.getSubjectName()), row.getType(), row.getDifficulty(), row.getStatus(),
                formatTime(row.getCreatedAt()), done, accuracy(done, correct));
    }

    private Map<String, List<Stage>> stagesByName() {
        Map<String, List<Stage>> grouped = new LinkedHashMap<>();
        for (Stage stage : stageMapper.selectList(null)) {
            grouped.computeIfAbsent(stage.getName(), key -> new ArrayList<>()).add(stage);
        }
        return grouped;
    }

    private String[] exportLine(QuestionRow row, List<QuestionOption> options) {
        Map<String, String> texts = new LinkedHashMap<>();
        List<String> correct = new ArrayList<>();
        for (QuestionOption option : options) {
            texts.put(option.getOptionKey(), option.getOptionText());
            if (option.getIsCorrect() != null && option.getIsCorrect() == 1) {
                correct.add(option.getOptionKey());
            }
        }
        String answer = row.getType() != null && (row.getType() == 1 || row.getType() == 2)
                ? String.join(",", correct)
                : nullToEmpty(row.getAnswer());
        return new String[]{
                nullToEmpty(row.getContent()),
                nullToEmpty(row.getStageName()),
                typeName(row.getType()),
                difficultyName(row.getDifficulty()),
                texts.getOrDefault("A", ""),
                texts.getOrDefault("B", ""),
                texts.getOrDefault("C", ""),
                texts.getOrDefault("D", ""),
                texts.getOrDefault("E", ""),
                texts.getOrDefault("F", ""),
                answer,
                nullToEmpty(row.getAnalysis()),
                nullToEmpty(row.getTags()),
                statusName(row.getStatus())
        };
    }

    private void validateFilter(Integer type, Integer difficulty, Integer status) {
        if (type != null && (type < 1 || type > 4)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "题型不正确");
        }
        if (difficulty != null && (difficulty < 1 || difficulty > 3)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "难度不正确");
        }
        if (status != null && status != 1 && status != 2 && status != 3 && status != 5) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "状态不正确");
        }
    }

    private static String typeName(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case 1 -> "单选";
            case 2 -> "多选";
            case 3 -> "判断";
            case 4 -> "简答";
            default -> "";
        };
    }

    private static String difficultyName(Integer difficulty) {
        if (difficulty == null) {
            return "";
        }
        return switch (difficulty) {
            case 1 -> "简单";
            case 2 -> "中等";
            case 3 -> "困难";
            default -> "";
        };
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "已上架";
            case 2 -> "已下架";
            case 3 -> "待审核";
            case 5 -> "已驳回";
            default -> "";
        };
    }

    private static Double accuracy(long done, long correct) {
        if (done <= 0) {
            return null;
        }
        return Math.round(correct * 1000.0 / done) / 10.0;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME.format(time);
    }

    private static String like(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().replace("|", "||").replace("%", "|%").replace("_", "|_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String label(String content) {
        String value = content == null ? "" : content.replace('\n', ' ').trim();
        return value.length() <= 40 ? value : value.substring(0, 40);
    }

    private String writeJson(List<QuestionDraft> drafts) {
        try {
            return objectMapper.writeValueAsString(drafts);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "保存导入预览失败");
        }
    }

    private List<QuestionDraft> readDrafts(String json) {
        try {
            List<QuestionDraft> drafts = objectMapper.readValue(json, DRAFTS);
            return drafts == null ? List.of() : drafts;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "导入数据已失效，请重新上传");
        }
    }

    private static String redisKey(String token) {
        return "question:import:" + token;
    }

    private record Prepared(Long stageId, int type, int difficulty, String content, String answer, String analysis,
                            String tags, List<OptionInput> options) {
    }

    public record QuestionDraft(Long stageId, Integer type, Integer difficulty, String content, String answer,
                                String analysis, String tags, List<OptionInput> options) {
    }
}
