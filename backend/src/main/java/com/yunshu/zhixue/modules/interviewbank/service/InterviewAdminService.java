package com.yunshu.zhixue.modules.interviewbank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.ImportConfirmRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.ImportConfirmResult;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.ImportPreview;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionCreateRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionDetail;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionItem;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.QuestionUpdateRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.Sample;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.SearchHit;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.SubjectOption;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.TopicOption;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.TopicSaveRequest;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.UploadAccepted;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.UploadStatus;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewRow;
import com.yunshu.zhixue.modules.interviewbank.dto.TopicCountRow;
import com.yunshu.zhixue.modules.interviewbank.entity.InterviewQuestion;
import com.yunshu.zhixue.modules.interviewbank.entity.InterviewTopic;
import com.yunshu.zhixue.modules.interviewbank.mapper.InterviewQuestionMapper;
import com.yunshu.zhixue.modules.interviewbank.mapper.InterviewTopicMapper;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InterviewAdminService {

    static final int SOURCE_PARSE = 1;
    static final int SOURCE_MANUAL = 2;
    static final int SOURCE_IMPORT = 3;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);
    private static final Duration PARSE_TTL = Duration.ofHours(2);
    private static final String IMPORT_PREFIX = "interview:import:";
    private static final String PARSE_PREFIX = "content:parse:";
    private static final DefaultRedisScript<String> TAKE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
              redis.call('DEL', KEYS[1])
            end
            return value
            """, String.class);

    private final InterviewQuestionMapper questionMapper;
    private final InterviewTopicMapper topicMapper;
    private final SubjectMapper subjectMapper;
    private final InterviewMarkdownParser parser;
    private final InterviewParseWorker worker;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OperationLogWriter operationLogWriter;

    public InterviewAdminService(InterviewQuestionMapper questionMapper, InterviewTopicMapper topicMapper,
                                 SubjectMapper subjectMapper, InterviewMarkdownParser parser, InterviewParseWorker worker,
                                 StringRedisTemplate redis, ObjectMapper objectMapper, OperationLogWriter operationLogWriter) {
        this.questionMapper = questionMapper;
        this.topicMapper = topicMapper;
        this.subjectMapper = subjectMapper;
        this.parser = parser;
        this.worker = worker;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.operationLogWriter = operationLogWriter;
    }

    public List<SubjectOption> subjects() {
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>().orderByAsc(Subject::getSortOrder).orderByAsc(Subject::getId))
                .stream()
                .map(subject -> new SubjectOption(subject.getId(), subject.getName()))
                .toList();
    }

    public PageResult<QuestionItem> page(String keyword, Long subjectId, Long topicId, String topic, Long topicSubjectId,
                                         Integer difficulty, Boolean unclassified, long page, long pageSize) {
        boolean onlyUntagged = Boolean.TRUE.equals(unclassified);
        if (onlyUntagged && (topicId != null || (topic != null && !topic.isBlank()))) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "只看未分类和主题筛选不能同时使用");
        }
        if (difficulty != null && (difficulty < 1 || difficulty > 3)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "难度不正确");
        }
        String topicName = null;
        Long scopedSubject = null;
        if (!onlyUntagged && topicId != null) {
            InterviewTopic row = topicMapper.selectById(topicId);
            if (row == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "主题不存在");
            }
            topicName = row.getName();
            scopedSubject = row.getSubjectId();
        } else if (!onlyUntagged && topic != null && !topic.isBlank()) {
            topicName = topic.trim();
            scopedSubject = topicSubjectId;
        }
        Page<InterviewRow> result = questionMapper.search(new Page<>(page, pageSize), like(keyword), subjectId,
                onlyUntagged, topicName, scopedSubject, difficulty);
        List<QuestionItem> list = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    public QuestionDetail detail(Long id) {
        return toDetail(requireQuestion(id));
    }

    @Transactional
    public void create(QuestionCreateRequest request) {
        requireSubject(request.subjectId());
        String content = requireContent(request.content());
        InterviewQuestion row = new InterviewQuestion();
        row.setSubjectId(request.subjectId());
        row.setContent(content);
        row.setReferenceAnswer(blankToNull(request.referenceAnswer()));
        row.setTopic(blankToNull(request.topic()));
        row.setDifficulty(request.difficulty());
        row.setTags(normalizeTags(request.tags()));
        row.setSourceType(SOURCE_MANUAL);
        row.setStatus(1);
        questionMapper.insert(row);
        operationLogWriter.write("新增", "面试题", label(content));
    }

    @Transactional
    public void update(Long id, QuestionUpdateRequest request) {
        InterviewQuestion question = requireQuestion(id);
        questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, question.getId())
                .set(InterviewQuestion::getTopic, blankToNull(request.topic()))
                .set(InterviewQuestion::getDifficulty, request.difficulty()));
        operationLogWriter.write("修改", "面试题", label(question.getContent()));
    }

    @Transactional
    public void delete(Long id) {
        InterviewQuestion question = requireQuestion(id);
        if (questionMapper.countScores(id) > 0) {
            throw new BizException(ErrorCode.HAS_RELATIONS, "这道题已经被面试记录引用，不能删除");
        }
        questionMapper.deleteById(id);
        operationLogWriter.write("删除", "面试题", label(question.getContent()));
    }

    public UploadAccepted startUpload(MultipartFile file, Long subjectId) {
        requireSubject(subjectId);
        String fileName = markdownName(file);
        String text = readText(file);
        String taskId = UUID.randomUUID().toString().replace("-", "");
        writeTask(taskId, new UploadStatus("parsing", 0, List.of(), null));
        worker.run(taskId, subjectId, fileName, text);
        return new UploadAccepted(taskId);
    }

    public UploadStatus uploadStatus(String taskId) {
        String json = redis.opsForValue().get(PARSE_PREFIX + taskId);
        if (json == null || json.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "解析任务不存在或已过期");
        }
        try {
            return objectMapper.readValue(json, UploadStatus.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "解析任务不存在或已过期");
        }
    }

    public ImportPreview preview(MultipartFile file) {
        String fileName = markdownName(file);
        List<Sample> items = parser.parse(readText(file));
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue().set(IMPORT_PREFIX + token, objectMapper.writeValueAsString(new ImportDraft(fileName, items)), PREVIEW_TTL);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "导入预览保存失败");
        }
        return new ImportPreview(token, fileName, file.getSize(), items.size(), head(items));
    }

    @Transactional
    public ImportConfirmResult confirm(ImportConfirmRequest request) {
        requireSubject(request.subjectId());
        String json = redis.execute(TAKE_SCRIPT, List.of(IMPORT_PREFIX + request.token().trim()));
        if (json == null || json.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入预览已失效，请重新上传");
        }
        ImportDraft draft = readDraft(json);
        if (draft.items() == null || draft.items().isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "没有可导入的题目");
        }
        insertBatch(request.subjectId(), blankToNull(request.topic()), request.difficulty(), normalizeTags(request.tags()),
                SOURCE_IMPORT, draft.fileName(), draft.items());
        operationLogWriter.write("新增", "面试题", "手动导入" + draft.items().size() + "道");
        return new ImportConfirmResult(draft.items().size());
    }

    public List<SearchHit> search(String keyword) {
        String like = like(keyword);
        if (like == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请输入检索关键词");
        }
        return questionMapper.searchHits(new Page<>(1, 20), like).getRecords().stream().map(this::toHit).toList();
    }

    public List<TopicOption> topics(Long subjectId) {
        List<InterviewTopic> all = topicMapper.selectList(new LambdaQueryWrapper<InterviewTopic>()
                .orderByAsc(InterviewTopic::getSortOrder)
                .orderByAsc(InterviewTopic::getId));
        List<TopicCountRow> counts = questionMapper.countByTopic(subjectId);
        Map<Long, String> subjects = subjectNames();
        List<TopicOption> options = new ArrayList<>();
        for (InterviewTopic topic : all) {
            if (subjectId != null && topic.getSubjectId() != null && !subjectId.equals(topic.getSubjectId())) {
                continue;
            }
            long count = 0;
            for (TopicCountRow row : counts) {
                if (!topic.getName().equals(row.getTopic())) {
                    continue;
                }
                if (topic.getSubjectId() == null || topic.getSubjectId().equals(row.getSubjectId())) {
                    count += row.getQuestionCount() == null ? 0 : row.getQuestionCount();
                }
            }
            options.add(new TopicOption(topic.getId(), topic.getName(), topic.getSubjectId(),
                    subjectName(subjects, topic.getSubjectId()), count, true, topic.getSortOrder() == null ? 0 : topic.getSortOrder()));
        }
        for (TopicCountRow row : counts) {
            if (covered(row, all)) {
                continue;
            }
            long count = row.getQuestionCount() == null ? 0 : row.getQuestionCount();
            options.add(new TopicOption(null, row.getTopic(), row.getSubjectId(), subjectName(subjects, row.getSubjectId()), count, false, 0));
        }
        return options;
    }

    @Transactional
    public void createTopic(TopicSaveRequest request) {
        String name = request.name().trim();
        Long subjectId = request.subjectId();
        if (subjectId != null) {
            requireSubject(subjectId);
        }
        ensureTopicNameFree(name, subjectId, null);
        InterviewTopic topic = new InterviewTopic();
        topic.setName(name);
        topic.setSubjectId(subjectId);
        topic.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        topicMapper.insert(topic);
        operationLogWriter.write("新增", "面试主题", name);
    }

    @Transactional
    public void updateTopic(Long id, TopicSaveRequest request) {
        InterviewTopic topic = requireTopic(id);
        String name = request.name().trim();
        Long subjectId = request.subjectId();
        if (subjectId != null) {
            requireSubject(subjectId);
        }
        ensureTopicNameFree(name, subjectId, id);
        if (!name.equals(topic.getName())) {
            questionMapper.renameTopic(topic.getName(), name, topic.getSubjectId());
        }
        topicMapper.update(null, new LambdaUpdateWrapper<InterviewTopic>()
                .eq(InterviewTopic::getId, id)
                .set(InterviewTopic::getName, name)
                .set(InterviewTopic::getSubjectId, subjectId)
                .set(InterviewTopic::getSortOrder, request.sortOrder() == null ? 0 : request.sortOrder()));
        operationLogWriter.write("修改", "面试主题", name);
    }

    @Transactional
    public void deleteTopic(Long id) {
        InterviewTopic topic = requireTopic(id);
        if (questionMapper.countUsingTopic(topic.getName(), topic.getSubjectId()) > 0) {
            throw new BizException(ErrorCode.HAS_RELATIONS, "该主题下还有题目，不能删除");
        }
        topicMapper.deleteById(id);
        operationLogWriter.write("删除", "面试主题", topic.getName());
    }

    @Transactional
    public void saveUploaded(long subjectId, String fileName, List<Sample> items) {
        insertBatch(subjectId, null, 2, null, SOURCE_PARSE, fileName, items);
        operationLogWriter.write("新增", "面试题", "文档解析" + items.size() + "道");
    }

    void writeTask(String taskId, UploadStatus status) {
        try {
            redis.opsForValue().set(PARSE_PREFIX + taskId, objectMapper.writeValueAsString(status), PARSE_TTL);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "解析任务保存失败");
        }
    }

    private void insertBatch(long subjectId, String topic, int difficulty, String tags, int sourceType, String fileName, List<Sample> items) {
        int seq = 1;
        for (Sample item : items) {
            InterviewQuestion row = new InterviewQuestion();
            row.setSubjectId(subjectId);
            row.setContent(item.content());
            row.setReferenceAnswer(blankToNull(item.referenceAnswer()));
            row.setTopic(topic);
            row.setDifficulty(difficulty);
            row.setTags(tags);
            row.setSourceType(sourceType);
            row.setSourceDocName(fileName);
            row.setSourceSeq(seq++);
            row.setStatus(1);
            questionMapper.insert(row);
        }
    }

    private InterviewQuestion requireQuestion(Long id) {
        InterviewQuestion question = questionMapper.selectById(id);
        if (question == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    private InterviewTopic requireTopic(Long id) {
        InterviewTopic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "主题不存在");
        }
        return topic;
    }

    private Subject requireSubject(Long subjectId) {
        if (subjectId == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择方向");
        }
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "方向不存在");
        }
        return subject;
    }

    private void ensureTopicNameFree(String name, Long subjectId, Long excludeId) {
        LambdaQueryWrapper<InterviewTopic> query = new LambdaQueryWrapper<InterviewTopic>().eq(InterviewTopic::getName, name);
        if (subjectId == null) {
            query.isNull(InterviewTopic::getSubjectId);
        } else {
            query.eq(InterviewTopic::getSubjectId, subjectId);
        }
        if (excludeId != null) {
            query.ne(InterviewTopic::getId, excludeId);
        }
        if (topicMapper.selectCount(query) > 0) {
            throw new BizException(ErrorCode.NAME_EXISTS, "主题名称已存在");
        }
    }

    private boolean covered(TopicCountRow row, List<InterviewTopic> topics) {
        for (InterviewTopic topic : topics) {
            if (!topic.getName().equals(row.getTopic())) {
                continue;
            }
            if (topic.getSubjectId() == null || topic.getSubjectId().equals(row.getSubjectId())) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, String> subjectNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Subject subject : subjectMapper.selectList(new LambdaQueryWrapper<Subject>())) {
            names.put(subject.getId(), subject.getName());
        }
        return names;
    }

    private static String subjectName(Map<Long, String> names, Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        return names.get(subjectId);
    }

    private QuestionItem toItem(InterviewRow row) {
        return new QuestionItem(row.getId(), row.getContent(), row.getSubjectId(), row.getSubjectName(), row.getDifficulty(),
                row.getTopic(), row.getTags(), row.getSourceType(), row.getSourceDocName(), row.getSourceSeq(), formatTime(row.getCreatedAt()));
    }

    private QuestionDetail toDetail(InterviewQuestion question) {
        Subject subject = subjectMapper.selectById(question.getSubjectId());
        return new QuestionDetail(question.getId(), question.getContent(), question.getReferenceAnswer(), question.getSubjectId(),
                subject == null ? "" : subject.getName(), question.getDifficulty(), question.getTopic(), question.getTags(),
                question.getSourceType(), question.getSourceDocName(), question.getSourceSeq(),
                formatTime(question.getCreatedAt()), formatTime(question.getUpdatedAt()));
    }

    private SearchHit toHit(InterviewRow row) {
        return new SearchHit(row.getId(), row.getContent(), row.getSubjectName(), row.getSourceType(), row.getSourceDocName(),
                row.getSourceSeq(), row.getTopic(), row.getDifficulty());
    }

    private ImportDraft readDraft(String json) {
        try {
            return objectMapper.readValue(json, ImportDraft.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入预览已失效，请重新上传");
        }
    }

    private static String markdownName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_FILE, "请选择 Markdown 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BizException(ErrorCode.BAD_FILE, "只支持 Markdown（.md）文件");
        }
        if (name.length() > 255) {
            throw new BizException(ErrorCode.BAD_FILE, "文件名过长");
        }
        return name;
    }

    private static String readText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BAD_FILE, "文件读取失败");
        }
    }

    private static String requireContent(String content) {
        String value = blankToNull(content);
        if (value == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "题干不能为空");
        }
        return value;
    }

    private static String normalizeTags(String tags) {
        String value = blankToNull(tags);
        if (value == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split("[,，]")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                parts.add(item);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String joined = String.join(",", parts);
        if (joined.length() > 255) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "标签不超过 255 字");
        }
        return joined;
    }

    private static List<Sample> head(List<Sample> items) {
        return items.size() <= 3 ? items : items.subList(0, 3);
    }

    private static String formatTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.atOffset(ZoneOffset.UTC).atZoneSameInstant(SHANGHAI).format(TIME);
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

    private static String label(String content) {
        String value = content == null ? "" : content.trim().replace('\n', ' ');
        return value.length() <= 40 ? value : value.substring(0, 40);
    }

    private record ImportDraft(String fileName, List<Sample> items) {
    }
}
