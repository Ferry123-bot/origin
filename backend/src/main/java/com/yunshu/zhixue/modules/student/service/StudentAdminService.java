package com.yunshu.zhixue.modules.student.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.BatchIdsRequest;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.BatchStatusRequest;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.InterviewItem;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.StatusRequest;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.StudentDetail;
import com.yunshu.zhixue.modules.student.dto.StudentPayloads.StudentItem;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentAdminService {

    private static final int EXPORT_LIMIT = 10_000;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StudentMapper studentMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OperationLogWriter operationLogWriter;

    public StudentAdminService(StudentMapper studentMapper, JdbcTemplate jdbcTemplate, OperationLogWriter operationLogWriter) {
        this.studentMapper = studentMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.operationLogWriter = operationLogWriter;
    }

    public PageResult<StudentItem> page(String keyword, Integer status, long page, long pageSize) {
        Page<Student> result = studentMapper.selectPage(new Page<>(page, pageSize), query(keyword, status));
        Map<Long, Counts> counts = counts(result.getRecords().stream().map(Student::getId).toList());
        List<StudentItem> list = result.getRecords().stream().map(student -> toItem(student, counts.get(student.getId()))).toList();
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    public StudentDetail detail(Long id) {
        Student student = requireStudent(id);
        Counts counts = counts(List.of(id)).getOrDefault(id, Counts.EMPTY);
        int accuracy = counts.practiceCount == 0 ? 0 : (int) Math.round(counts.correctCount * 100.0 / counts.practiceCount);
        return new StudentDetail(student.getId(), student.getNickname(), student.getAvatar(), student.getMobile(),
                student.getEmail(), student.getStatus(), student.getCreatedAt(), student.getLastLoginAt(),
                counts.studyDays, counts.courseCount, counts.practiceCount, accuracy, counts.interviewCount);
    }

    public PageResult<InterviewItem> interviews(Long id, long page, long pageSize) {
        requireStudent(id);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_record WHERE delete_flag = 0 AND student_id = ?", Long.class, id);
        long count = total == null ? 0 : total;
        List<InterviewItem> list = jdbcTemplate.query("""
                SELECT r.id, COALESCE(s.name, '—') AS subject_name, r.difficulty, r.overall_score,
                       r.question_count, r.completed_at
                FROM interview_record r
                LEFT JOIN subject s ON s.id = r.subject_id AND s.delete_flag = 0
                WHERE r.delete_flag = 0 AND r.student_id = ?
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new InterviewItem(rs.getLong("id"), rs.getString("subject_name"),
                rs.getInt("difficulty"), score(rs), rs.getInt("question_count"), time(rs, "completed_at")),
                id, pageSize, (page - 1) * pageSize);
        return new PageResult<>(list, count, page, pageSize);
    }

    @Transactional
    public void changeStatus(Long id, StatusRequest request) {
        Student student = requireStudent(id);
        student.setStatus(request.status());
        studentMapper.updateById(student);
        operationLogWriter.write("禁用启用", "学员", student.getNickname());
    }

    @Transactional
    public void batchStatus(BatchStatusRequest request) {
        List<Student> students = loadStudents(request.ids());
        for (Student student : students) {
            student.setStatus(request.status());
            studentMapper.updateById(student);
        }
        operationLogWriter.write("禁用启用", "学员", joinNames(students));
    }

    @Transactional
    public void delete(Long id) {
        Student student = requireStudent(id);
        studentMapper.deleteById(id);
        operationLogWriter.write("删除", "学员", student.getNickname());
    }

    @Transactional
    public void batchDelete(BatchIdsRequest request) {
        List<Student> students = loadStudents(request.ids());
        for (Student student : students) {
            studentMapper.deleteById(student.getId());
        }
        operationLogWriter.write("删除", "学员", joinNames(students));
    }

    public ResponseEntity<byte[]> export(String keyword, Integer status) {
        long total = studentMapper.selectCount(query(keyword, status));
        if (total > EXPORT_LIMIT) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "导出超过 1 万行，请缩小筛选范围");
        }
        List<Student> students = studentMapper.selectList(query(keyword, status));
        Map<Long, Counts> counts = counts(students.stream().map(Student::getId).toList());
        StringBuilder csv = new StringBuilder("\uFEFF昵称,手机号,邮箱,状态,注册时间,最后登录,学习天数,刷题数,面试次数\n");
        for (Student student : students) {
            Counts row = counts.getOrDefault(student.getId(), Counts.EMPTY);
            csv.append(cell(student.getNickname())).append(',')
                    .append(cell(student.getMobile())).append(',')
                    .append(cell(student.getEmail())).append(',')
                    .append(cell(student.getStatus() != null && student.getStatus() == 1 ? "已启用" : "已禁用")).append(',')
                    .append(cell(format(student.getCreatedAt()))).append(',')
                    .append(cell(format(student.getLastLoginAt()))).append(',')
                    .append(row.studyDays).append(',')
                    .append(row.practiceCount).append(',')
                    .append(row.interviewCount)
                    .append('\n');
        }
        operationLogWriter.write("导出", "学员", "导出 " + students.size() + " 条");
        ContentDisposition disposition = ContentDisposition.attachment().filename("students.csv", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private LambdaQueryWrapper<Student> query(String keyword, Integer status) {
        LambdaQueryWrapper<Student> query = Wrappers.<Student>lambdaQuery()
                .orderByDesc(Student::getCreatedAt)
                .orderByDesc(Student::getId);
        if (status != null) {
            if (status != 0 && status != 1) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "状态只能是已启用或已禁用");
            }
            query.eq(Student::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String text = like(keyword);
            query.and(wrapper -> wrapper.apply("nickname LIKE {0} ESCAPE '|'", text)
                    .or()
                    .apply("mobile LIKE {0} ESCAPE '|'", text));
        }
        return query;
    }

    private List<Student> loadStudents(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        List<Student> students = studentMapper.selectBatchIds(distinct);
        if (students.size() != distinct.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "学员不存在");
        }
        Map<Long, Student> byId = new LinkedHashMap<>();
        students.forEach(student -> byId.put(student.getId(), student));
        return distinct.stream().map(byId::get).toList();
    }

    private Student requireStudent(Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "学员不存在");
        }
        return student;
    }

    private Map<Long, Counts> counts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Counts> map = new HashMap<>();
        String placeholders = ids.stream().map(id -> "?").reduce((left, right) -> left + "," + right).orElse("");
        Object[] args = ids.toArray();
        jdbcTemplate.query("""
                SELECT student_id,
                       COUNT(DISTINCT DATE(DATE_ADD(practiced_at, INTERVAL 8 HOUR))) AS study_days,
                       COUNT(*) AS practice_count,
                       COALESCE(SUM(is_correct), 0) AS correct_count
                FROM practice_record
                WHERE delete_flag = 0 AND student_id IN (%s)
                GROUP BY student_id
                """.formatted(placeholders), rs -> {
            countsOf(map, rs.getLong("student_id")).studyDays = rs.getLong("study_days");
            countsOf(map, rs.getLong("student_id")).practiceCount = rs.getLong("practice_count");
            countsOf(map, rs.getLong("student_id")).correctCount = rs.getLong("correct_count");
        }, args);
        jdbcTemplate.query("""
                SELECT student_id, COUNT(*) AS course_count
                FROM route_course_completion
                WHERE delete_flag = 0 AND student_id IN (%s)
                GROUP BY student_id
                """.formatted(placeholders), rs -> {
            countsOf(map, rs.getLong("student_id")).courseCount = rs.getLong("course_count");
        }, args);
        jdbcTemplate.query("""
                SELECT student_id, COUNT(*) AS interview_count
                FROM interview_record
                WHERE delete_flag = 0 AND student_id IN (%s)
                GROUP BY student_id
                """.formatted(placeholders), rs -> {
            countsOf(map, rs.getLong("student_id")).interviewCount = rs.getLong("interview_count");
        }, args);
        return map;
    }

    private Counts countsOf(Map<Long, Counts> map, long id) {
        return map.computeIfAbsent(id, key -> new Counts());
    }

    private StudentItem toItem(Student student, Counts counts) {
        Counts row = counts == null ? Counts.EMPTY : counts;
        return new StudentItem(student.getId(), student.getNickname(), student.getAvatar(), student.getMobile(),
                student.getCreatedAt(), row.studyDays, row.practiceCount, row.interviewCount, student.getStatus());
    }

    private BigDecimal score(ResultSet rs) throws SQLException {
        BigDecimal value = rs.getBigDecimal("overall_score");
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private String joinNames(List<Student> students) {
        String text = students.stream().map(Student::getNickname).reduce((left, right) -> left + "、" + right).orElse("");
        return text.length() <= 255 ? text : text.substring(0, 252) + "...";
    }

    private String like(String keyword) {
        return "%" + keyword.trim().replace("|", "||").replace("%", "|%").replace("_", "|_") + "%";
    }

    private String format(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.atOffset(ZoneOffset.UTC).atZoneSameInstant(SHANGHAI).format(FORMATTER);
    }

    private String cell(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static final class Counts {
        private static final Counts EMPTY = new Counts();
        private long studyDays;
        private long practiceCount;
        private long correctCount;
        private long courseCount;
        private long interviewCount;
    }
}
