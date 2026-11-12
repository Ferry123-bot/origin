package com.yunshu.zhixue.modules.study.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.question.entity.QuestionOption;
import com.yunshu.zhixue.modules.question.mapper.QuestionOptionMapper;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeStartRequest;
import com.yunshu.zhixue.modules.study.dto.PracticePayloads.PracticeStartResult;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.RedoStartRequest;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.WrongOption;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.WrongQuestionItem;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.WrongStats;
import com.yunshu.zhixue.modules.study.dto.WrongBookPayloads.WrongTopItem;
import com.yunshu.zhixue.modules.study.dto.WrongBookStatsRow;
import com.yunshu.zhixue.modules.study.dto.WrongQuestionRow;
import com.yunshu.zhixue.modules.study.entity.WrongQuestion;
import com.yunshu.zhixue.modules.study.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WrongBookPortalService {

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final StudentMapper studentMapper;
    private final PracticePortalService practicePortalService;

    public WrongBookPortalService(WrongQuestionMapper wrongQuestionMapper, QuestionOptionMapper questionOptionMapper,
                                  StudentMapper studentMapper, PracticePortalService practicePortalService) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.studentMapper = studentMapper;
        this.practicePortalService = practicePortalService;
    }

    public WrongStats stats() {
        long studentId = requireStudentId();
        WrongBookStatsRow row = wrongQuestionMapper.stats(studentId);
        long total = row == null || row.getTotal() == null ? 0 : row.getTotal();
        long toReview = row == null || row.getToReviewCount() == null ? 0 : row.getToReviewCount();
        long mastered = row == null || row.getMasteredCount() == null ? 0 : row.getMasteredCount();
        int rate = total == 0 ? 0 : (int) Math.round(mastered * 100.0 / total);
        List<WrongTopItem> top3 = wrongQuestionMapper.top3(studentId).stream()
                .map(item -> new WrongTopItem(item.getId(), item.getQuestionId(), item.getContent(),
                        item.getErrorCount() == null ? 0 : item.getErrorCount()))
                .toList();
        return new WrongStats(total, toReview, mastered, rate, top3);
    }

    public PageResult<WrongQuestionItem> page(Integer status, Integer type, Integer days, String sort, long page, long pageSize) {
        long studentId = requireStudentId();
        if (status != null && status != 1 && status != 2) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "状态无效");
        }
        if (type != null && (type < 1 || type > 4)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "题型无效");
        }
        if (days != null && days != 7 && days != 30) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "时间范围无效");
        }
        if (sort != null && !sort.isBlank() && !"recent".equals(sort) && !"count".equals(sort)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "排序无效");
        }
        String order = "count".equals(sort) ? "count" : "recent";
        LocalDateTime since = days == null ? null : LocalDateTime.now(ZoneOffset.UTC).minusDays(days);
        long total = wrongQuestionMapper.countList(studentId, status, type, since);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, page, pageSize);
        }
        List<WrongQuestionRow> rows = wrongQuestionMapper.list(studentId, status, type, since, order,
                (page - 1) * pageSize, pageSize);
        Map<Long, List<QuestionOption>> options = loadOptions(rows);
        List<WrongQuestionItem> list = rows.stream()
                .map(row -> toItem(row, options.getOrDefault(row.getQuestionId(), List.of())))
                .toList();
        return new PageResult<>(list, total, page, pageSize);
    }

    public PracticeStartResult redo(RedoStartRequest request) {
        long studentId = requireStudentId();
        if (request.ids() == null || request.ids().isEmpty() || request.ids().size() > 100) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择 1 到 100 道错题");
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : request.ids()) {
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择 1 到 100 道错题");
        }
        List<WrongQuestion> rows = wrongQuestionMapper.selectList(Wrappers.<WrongQuestion>lambdaQuery()
                .eq(WrongQuestion::getStudentId, studentId)
                .in(WrongQuestion::getId, ids));
        Map<Long, WrongQuestion> byId = new LinkedHashMap<>();
        for (WrongQuestion row : rows) {
            byId.put(row.getId(), row);
        }
        List<Long> questionIds = new ArrayList<>();
        for (Long id : ids) {
            WrongQuestion row = byId.get(id);
            if (row == null || row.getStatus() == null || row.getStatus() != 1) {
                throw new BizException(ErrorCode.WRONG_QUESTION_NOT_FOUND, "只能重做待复习的错题");
            }
            questionIds.add(row.getQuestionId());
        }
        return practicePortalService.start(new PracticeStartRequest("wrong_redo", null, null, null, null, null, null, 1, questionIds));
    }

    public void mastered(long id) {
        long studentId = requireStudentId();
        WrongQuestion row = own(studentId, id);
        if (row.getStatus() == null || row.getStatus() != 1) {
            throw new BizException(ErrorCode.WRONG_QUESTION_NOT_FOUND, "该错题已掌握");
        }
        WrongQuestion update = new WrongQuestion();
        update.setId(id);
        update.setStatus(2);
        wrongQuestionMapper.updateById(update);
    }

    public void remove(long id) {
        long studentId = requireStudentId();
        own(studentId, id);
        int deleted = wrongQuestionMapper.delete(Wrappers.<WrongQuestion>lambdaQuery()
                .eq(WrongQuestion::getId, id)
                .eq(WrongQuestion::getStudentId, studentId));
        if (deleted == 0) {
            throw new BizException(ErrorCode.WRONG_QUESTION_NOT_FOUND);
        }
    }

    private WrongQuestion own(long studentId, long id) {
        WrongQuestion row = wrongQuestionMapper.selectOne(Wrappers.<WrongQuestion>lambdaQuery()
                .eq(WrongQuestion::getId, id)
                .eq(WrongQuestion::getStudentId, studentId));
        if (row == null) {
            throw new BizException(ErrorCode.WRONG_QUESTION_NOT_FOUND);
        }
        return row;
    }

    private WrongQuestionItem toItem(WrongQuestionRow row, List<QuestionOption> options) {
        return new WrongQuestionItem(
                row.getId(),
                row.getQuestionId(),
                row.getType() == null ? 0 : row.getType(),
                row.getContent(),
                optionViews(row, options),
                correctAnswer(row, options),
                row.getLastAnswer() == null ? "" : row.getLastAnswer(),
                row.getAnalysis(),
                row.getErrorCount() == null ? 0 : row.getErrorCount(),
                row.getCorrectedCount() == null ? 0 : row.getCorrectedCount(),
                row.getStatus() == null ? 1 : row.getStatus(),
                row.getLastWrongAt(),
                row.getSubjectName(),
                row.getStageName());
    }

    private List<WrongOption> optionViews(WrongQuestionRow row, List<QuestionOption> options) {
        if ((options == null || options.isEmpty()) && row.getType() != null && row.getType() == 3) {
            return List.of(new WrongOption("正确", "正确"), new WrongOption("错误", "错误"));
        }
        if (options == null) {
            return List.of();
        }
        return options.stream().map(option -> new WrongOption(option.getOptionKey(), option.getOptionText())).toList();
    }

    private String correctAnswer(WrongQuestionRow row, List<QuestionOption> options) {
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

    private Map<Long, List<QuestionOption>> loadOptions(List<WrongQuestionRow> rows) {
        List<Long> ids = rows.stream().map(WrongQuestionRow::getQuestionId).distinct().toList();
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
}
