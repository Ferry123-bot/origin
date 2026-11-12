package com.yunshu.zhixue.modules.question.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.question.dto.PortalQuestionPayloads.OptionItem;
import com.yunshu.zhixue.modules.question.dto.PortalQuestionPayloads.PortalQuestionDetail;
import com.yunshu.zhixue.modules.question.dto.PortalQuestionPayloads.PortalQuestionItem;
import com.yunshu.zhixue.modules.question.dto.PortalQuestionRow;
import com.yunshu.zhixue.modules.question.entity.QuestionOption;
import com.yunshu.zhixue.modules.question.mapper.PortalQuestionMapper;
import com.yunshu.zhixue.modules.question.mapper.QuestionOptionMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionPortalService {

    private final PortalQuestionMapper portalQuestionMapper;
    private final QuestionOptionMapper questionOptionMapper;

    public QuestionPortalService(PortalQuestionMapper portalQuestionMapper, QuestionOptionMapper questionOptionMapper) {
        this.portalQuestionMapper = portalQuestionMapper;
        this.questionOptionMapper = questionOptionMapper;
    }

    public PageResult<PortalQuestionItem> page(Long subjectId, Long stageId, Integer type, Integer difficulty,
                                               String keyword, long page, long pageSize) {
        if (type != null && (type < 1 || type > 4)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "题型不正确");
        }
        if (difficulty != null && (difficulty < 1 || difficulty > 3)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "难度不正确");
        }
        Page<PortalQuestionRow> result = portalQuestionMapper.search(
                new Page<>(page, pageSize), subjectId, stageId, type, difficulty, like(keyword));
        List<PortalQuestionItem> list = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    public PortalQuestionDetail detail(Long id) {
        PortalQuestionRow row = portalQuestionMapper.findPublished(id);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        List<QuestionOption> options = questionOptionMapper.selectList(Wrappers.<QuestionOption>lambdaQuery()
                .eq(QuestionOption::getQuestionId, id)
                .orderByAsc(QuestionOption::getSortOrder)
                .orderByAsc(QuestionOption::getId));
        PortalQuestionDetail detail = new PortalQuestionDetail();
        detail.setId(row.getId());
        detail.setType(row.getType() == null ? 0 : row.getType());
        detail.setDifficulty(row.getDifficulty() == null ? 0 : row.getDifficulty());
        detail.setContent(row.getContent());
        detail.setSubjectName(row.getSubjectName());
        detail.setStageName(row.getStageName());
        detail.setTags(row.getTags());
        detail.setOptions(options.stream().map(option -> new OptionItem(option.getOptionKey(), option.getOptionText())).toList());
        Long studentId = currentStudentId();
        if (studentId != null) {
            detail.setAnswer(correctAnswer(row, options));
            detail.setAnalysis(row.getAnalysis() == null ? "" : row.getAnalysis());
            detail.setInWrongBook(portalQuestionMapper.countWrong(studentId, id) > 0);
        }
        return detail;
    }

    private PortalQuestionItem toItem(PortalQuestionRow row) {
        int done = saturate(row.getDoneCount());
        int correct = saturate(row.getCorrectCount());
        int accuracy = done == 0 ? 0 : (int) Math.round(correct * 100.0 / done);
        return new PortalQuestionItem(
                row.getId(),
                row.getType() == null ? 0 : row.getType(),
                row.getDifficulty() == null ? 0 : row.getDifficulty(),
                row.getContent(),
                row.getSubjectName(),
                row.getStageName(),
                row.getTags(),
                done,
                accuracy);
    }

    private String correctAnswer(PortalQuestionRow row, List<QuestionOption> options) {
        int type = row.getType() == null ? 0 : row.getType();
        if (type == 1 || type == 2) {
            return options.stream()
                    .filter(option -> option.getIsCorrect() != null && option.getIsCorrect() == 1)
                    .map(QuestionOption::getOptionKey)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
        }
        return row.getAnswer() == null ? "" : row.getAnswer();
    }

    private Long currentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int saturate(Long value) {
        if (value == null || value < 0) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private static String like(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > 100) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "关键词过长");
        }
        String escaped = trimmed.replace("|", "||").replace("%", "|%").replace("_", "|_");
        return "%" + escaped + "%";
    }
}
