package com.yunshu.zhixue.modules.course.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseDraft;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseSaveRequest;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseStats;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseSubjectView;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.CourseView;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.ImportConfirmResult;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.ImportPreview;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.ImportRow;
import com.yunshu.zhixue.modules.course.dto.CoursePayloads.SubjectOption;
import com.yunshu.zhixue.modules.course.entity.Course;
import com.yunshu.zhixue.modules.course.entity.CourseSubject;
import com.yunshu.zhixue.modules.course.mapper.CourseMapper;
import com.yunshu.zhixue.modules.course.mapper.CourseSubjectMapper;
import com.yunshu.zhixue.modules.course.service.CourseWorkbook.ParsedCourse;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseAdminService {

    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<CourseDraft>> DRAFTS = new TypeReference<>() {
    };
    private static final DefaultRedisScript<String> TAKE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
              redis.call('DEL', KEYS[1])
            end
            return value
            """, String.class);

    private final CourseMapper courseMapper;
    private final CourseSubjectMapper courseSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final CourseWorkbook workbook;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OperationLogWriter operationLogWriter;

    public CourseAdminService(CourseMapper courseMapper, CourseSubjectMapper courseSubjectMapper, SubjectMapper subjectMapper,
                              CourseWorkbook workbook, JdbcTemplate jdbcTemplate, StringRedisTemplate redis,
                              ObjectMapper objectMapper, OperationLogWriter operationLogWriter) {
        this.courseMapper = courseMapper;
        this.courseSubjectMapper = courseSubjectMapper;
        this.subjectMapper = subjectMapper;
        this.workbook = workbook;
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.operationLogWriter = operationLogWriter;
    }

    public CourseStats stats() {
        long minutes = courseMapper.sumDurationMinutes();
        double hours = Math.round(minutes / 60.0 * 10.0) / 10.0;
        List<SubjectOption> subjects = subjectMapper.selectList(Wrappers.<Subject>lambdaQuery()
                        .orderByAsc(Subject::getSortOrder)
                        .orderByAsc(Subject::getId))
                .stream()
                .map(item -> new SubjectOption(item.getId(), item.getName()))
                .toList();
        return new CourseStats(courseMapper.selectCount(Wrappers.lambdaQuery()), hours, courseMapper.countLinkedSubjects(), subjects);
    }

    public PageResult<CourseView> page(String keyword, Long subjectId, Integer source, long page, long pageSize) {
        if (subjectId != null && subjectId < 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "学科筛选不正确");
        }
        if (source != null && source != 1 && source != 2) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "来源筛选不正确");
        }
        Page<Course> result = courseMapper.search(new Page<>(page, pageSize), likeKeyword(keyword), subjectId, source);
        return new PageResult<>(toViews(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public void create(CourseSaveRequest request) {
        Course course = new Course();
        fill(course, request);
        try {
            courseMapper.insert(course);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "BV号已存在");
        }
        replaceSubjects(course.getId(), extras(request));
        operationLogWriter.write("新增", "课程", course.getName());
    }

    @Transactional
    public void update(Long id, CourseSaveRequest request) {
        Course course = requireCourse(id);
        fill(course, request);
        course.setId(id);
        try {
            writeCourse(course);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "BV号已存在");
        }
        replaceSubjects(id, extras(request));
        operationLogWriter.write("修改", "课程", course.getName());
    }

    @Transactional
    public void delete(Long id) {
        Course course = requireCourse(id);
        if (courseMapper.countRouteRefs(id) > 0) {
            throw new BizException(ErrorCode.HAS_RELATIONS, "该课程已被学习路线引用，不能删除");
        }
        jdbcTemplate.update("UPDATE course SET bvid = NULL WHERE id = ?", id);
        courseMapper.deleteById(id);
        jdbcTemplate.update("DELETE FROM course_subject WHERE course_id = ?", id);
        operationLogWriter.write("删除", "课程", course.getName());
    }

    public ImportPreview preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_FILE, "请上传 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw new BizException(ErrorCode.BAD_FILE, "请上传 .xlsx 或 .xls 文件");
        }
        List<ParsedCourse> parsed;
        try (var input = file.getInputStream()) {
            parsed = workbook.read(input, subjectNameMap(), existingBvids(), existingNames());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "无法读取 Excel 文件，请使用模板重新填写");
        }
        List<ImportRow> rows = new ArrayList<>();
        List<CourseDraft> drafts = new ArrayList<>();
        int valid = 0;
        int warning = 0;
        int invalid = 0;
        for (ParsedCourse item : parsed) {
            String duration = item.durationMinutes() == null ? "" : CourseWorkbook.formatDuration(item.durationMinutes());
            rows.add(new ImportRow(item.row(), item.name(), item.subjects(), item.sourceLabel(), duration,
                    item.bvidText(), item.status(), item.messages()));
            if ("ok".equals(item.status())) {
                valid++;
            } else if ("warning".equals(item.status())) {
                warning++;
            } else {
                invalid++;
            }
            if (!"error".equals(item.status()) && item.durationMinutes() != null && item.source() != null) {
                drafts.add(new CourseDraft(item.name(), item.coverUrl(), item.durationMinutes(), item.source(),
                        item.bvid(), item.link(), item.subjectId(), item.subjectIds()));
            }
        }
        String token = null;
        if (!drafts.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "");
            redis.opsForValue().set(redisKey(token), writeJson(drafts), PREVIEW_TTL);
        }
        return new ImportPreview(token, parsed.size(), valid, warning, invalid, rows);
    }

    @Transactional
    public ImportConfirmResult confirm(String token) {
        if (token == null || !token.matches("[0-9a-fA-F]{32}")) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入预览已失效，请重新上传");
        }
        String key = redisKey(token);
        String json = redis.execute(TAKE_SCRIPT, List.of(key));
        if (json == null || json.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入预览已失效，请重新上传");
        }
        List<CourseDraft> drafts = readDrafts(json);
        if (drafts.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "没有可导入的数据");
        }
        try {
            for (CourseDraft draft : drafts) {
                insertDraft(draft);
            }
        } catch (RuntimeException ex) {
            redis.opsForValue().set(key, json, PREVIEW_TTL);
            throw ex;
        }
        operationLogWriter.write("新增", "课程", "批量导入 " + drafts.size() + " 门课程");
        return new ImportConfirmResult(drafts.size());
    }

    public byte[] template() {
        return workbook.template();
    }

    private void insertDraft(CourseDraft draft) {
        CourseSaveRequest request = new CourseSaveRequest(draft.name(), draft.coverUrl(), draft.durationMinutes(),
                draft.source(), draft.bvid(), draft.link(), draft.subjectId(), draft.subjectIds());
        Course course = new Course();
        fill(course, request);
        try {
            courseMapper.insert(course);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "BV号已存在" + (draft.bvid() == null ? "" : "：" + draft.bvid()));
        }
        replaceSubjects(course.getId(), extras(request));
    }

    private void fill(Course course, CourseSaveRequest request) {
        Binding binding = bind(request.subjectId(), request.subjectIds());
        String bvid = normalizeBvid(request.source(), request.bvid());
        course.setName(request.name().trim());
        course.setCoverUrl(normalizeUrl(request.coverUrl(), "封面"));
        course.setDurationMinutes(request.durationMinutes());
        course.setSource(request.source());
        course.setBvid(bvid);
        course.setLink(normalizeLink(request.link(), request.source(), bvid));
        course.setSubjectId(binding.subjectId());
    }

    private List<Long> extras(CourseSaveRequest request) {
        return bind(request.subjectId(), request.subjectIds()).extras();
    }

    private Binding bind(Long subjectId, List<Long> subjectIds) {
        List<Long> extras = new ArrayList<>();
        if (subjectIds != null) {
            for (Long id : subjectIds) {
                if (id != null && !extras.contains(id)) {
                    extras.add(id);
                }
            }
        }
        if (subjectId == null) {
            if (!extras.isEmpty()) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "选择其他学科时不能再关联具体学科");
            }
            return new Binding(null, List.of());
        }
        if (subjectId <= 0 || extras.stream().anyMatch(id -> id <= 0)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "所属学科不正确");
        }
        if (extras.contains(subjectId)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "关联学科不能与所属学科重复");
        }
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(subjectId);
        ids.addAll(extras);
        List<Subject> found = subjectMapper.selectBatchIds(ids);
        if (found.size() != ids.size()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "学科不存在");
        }
        return new Binding(subjectId, List.copyOf(extras));
    }

    private void writeCourse(Course course) {
        int updated = jdbcTemplate.update("""
                UPDATE course
                SET name = ?, cover_url = ?, duration_minutes = ?, source = ?, bvid = ?, link = ?, subject_id = ?, updated_at = ?
                WHERE id = ? AND delete_flag = 0
                """, course.getName(), course.getCoverUrl(), course.getDurationMinutes(), course.getSource(),
                course.getBvid(), course.getLink(), course.getSubjectId(), LocalDateTime.now(ZoneOffset.UTC), course.getId());
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
    }

    private void replaceSubjects(Long courseId, List<Long> extras) {
        jdbcTemplate.update("DELETE FROM course_subject WHERE course_id = ?", courseId);
        for (Long subjectId : extras) {
            CourseSubject row = new CourseSubject();
            row.setCourseId(courseId);
            row.setSubjectId(subjectId);
            courseSubjectMapper.insert(row);
        }
    }

    private List<CourseView> toViews(List<Course> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (Subject subject : subjectMapper.selectList(Wrappers.lambdaQuery())) {
            names.put(subject.getId(), subject.getName());
        }
        List<Long> ids = courses.stream().map(Course::getId).toList();
        Map<Long, List<Long>> extras = new HashMap<>();
        courseSubjectMapper.selectList(Wrappers.<CourseSubject>lambdaQuery()
                        .in(CourseSubject::getCourseId, ids)
                        .orderByAsc(CourseSubject::getId))
                .forEach(item -> extras.computeIfAbsent(item.getCourseId(), key -> new ArrayList<>()).add(item.getSubjectId()));
        return courses.stream().map(course -> toView(course, names, extras.getOrDefault(course.getId(), List.of()))).toList();
    }

    private CourseView toView(Course course, Map<Long, String> names, List<Long> extras) {
        List<CourseSubjectView> subjects = new ArrayList<>();
        if (course.getSubjectId() != null) {
            subjects.add(new CourseSubjectView(course.getSubjectId(), names.getOrDefault(course.getSubjectId(), "")));
        }
        for (Long extra : extras) {
            if (!extra.equals(course.getSubjectId())) {
                subjects.add(new CourseSubjectView(extra, names.getOrDefault(extra, "")));
            }
        }
        return new CourseView(course.getId(), course.getName(), course.getCoverUrl(), course.getDurationMinutes(),
                CourseWorkbook.formatDuration(course.getDurationMinutes()), course.getSource(), course.getBvid(),
                course.getLink(), course.getSubjectId(), course.getSubjectId() == null, subjects);
    }

    private Course requireCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    private Map<String, Long> subjectNameMap() {
        Map<String, Long> names = new HashMap<>();
        for (Subject subject : subjectMapper.selectList(Wrappers.lambdaQuery())) {
            names.put(subject.getName(), subject.getId());
        }
        return names;
    }

    private Set<String> existingNames() {
        Set<String> names = new HashSet<>();
        for (Course course : courseMapper.selectList(Wrappers.<Course>lambdaQuery().select(Course::getName))) {
            names.add(course.getName());
        }
        return names;
    }

    private Set<String> existingBvids() {
        Set<String> bvids = new HashSet<>();
        for (Course course : courseMapper.selectList(Wrappers.<Course>lambdaQuery().select(Course::getBvid).isNotNull(Course::getBvid))) {
            if (course.getBvid() != null) {
                bvids.add(course.getBvid().toLowerCase(Locale.ROOT));
            }
        }
        return bvids;
    }

    private String normalizeBvid(Integer source, String bvid) {
        String value = blankToNull(bvid);
        if (value != null && !value.matches("^BV[0-9A-Za-z]{10}$")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "BV号格式不正确，形如 BV1xx411c7mD");
        }
        if (source == 1 && value == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "来源为 B站时须填写 BV号");
        }
        return value;
    }

    private String normalizeLink(String link, Integer source, String bvid) {
        String value = blankToNull(link);
        if (value == null && source == 1 && bvid != null) {
            return "https://www.bilibili.com/video/" + bvid;
        }
        if (value != null && (value.length() > 500 || !value.matches("(?i)^https?://\\S+$"))) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "课程链接须为 http 或 https 地址");
        }
        return value;
    }

    private String normalizeUrl(String url, String label) {
        String value = blankToNull(url);
        if (value != null && (value.length() > 500 || !value.matches("(?i)^https?://\\S+$"))) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, label + "须为 http 或 https 地址");
        }
        return value;
    }

    private String likeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().replace("|", "||").replace("%", "|%").replace("_", "|_");
        return "%" + escaped + "%";
    }

    private String writeJson(List<CourseDraft> drafts) {
        try {
            return objectMapper.writeValueAsString(drafts);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "保存导入预览失败");
        }
    }

    private List<CourseDraft> readDrafts(String json) {
        try {
            List<CourseDraft> drafts = objectMapper.readValue(json, DRAFTS);
            return drafts == null ? List.of() : drafts;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.IMPORT_INVALID, "导入数据已失效，请重新上传");
        }
    }

    private static String redisKey(String token) {
        return "course:import:" + token;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Binding(Long subjectId, List<Long> extras) {
    }
}
