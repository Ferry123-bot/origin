package com.yunshu.zhixue.modules.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.AdminStageView;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.AdminSubjectView;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.SortRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.StageSaveRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.StageStatusRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.SubjectSaveRequest;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.SubjectStats;
import com.yunshu.zhixue.modules.subject.entity.Stage;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.StageMapper;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubjectAdminService {

    private final SubjectMapper subjectMapper;
    private final StageMapper stageMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OperationLogWriter operationLogWriter;

    public SubjectAdminService(SubjectMapper subjectMapper, StageMapper stageMapper, JdbcTemplate jdbcTemplate,
                               OperationLogWriter operationLogWriter) {
        this.subjectMapper = subjectMapper;
        this.stageMapper = stageMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.operationLogWriter = operationLogWriter;
    }

    public SubjectStats stats() {
        return new SubjectStats(
                subjectMapper.selectCount(Wrappers.<Subject>lambdaQuery()),
                subjectMapper.countSubjectsWithStages(),
                subjectMapper.countStages(),
                subjectMapper.countQuestions());
    }

    public List<AdminSubjectView> list() {
        return orderedSubjects().stream().map(this::toView).toList();
    }

    @Transactional
    public void create(SubjectSaveRequest request) {
        Subject subject = new Subject();
        fill(subject, request);
        try {
            subjectMapper.insert(subject);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "学科名称已存在");
        }
        operationLogWriter.write("新增", "学科", subject.getName());
    }

    @Transactional
    public void update(Long id, SubjectSaveRequest request) {
        Subject subject = requireSubject(id);
        fill(subject, request);
        try {
            subjectMapper.updateById(subject);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "学科名称已存在");
        }
        operationLogWriter.write("修改", "学科", subject.getName());
    }

    @Transactional
    public void sort(SortRequest request) {
        move(orderedSubjects(), request, "学科");
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = requireSubject(id);
        long stages = stageMapper.selectCount(Wrappers.<Stage>lambdaQuery().eq(Stage::getSubjectId, id));
        long questions = subjectMapper.countQuestionsBySubject(id);
        if (stages > 0 && questions > 0) {
            throw new BizException(ErrorCode.HAS_RELATIONS,
                    "该学科下有 " + stages + " 个阶段和 " + questions + " 道题目，请先处理后再删除");
        }
        if (stages > 0) {
            throw new BizException(ErrorCode.HAS_RELATIONS,
                    "该学科下有 " + stages + " 个阶段，请先删除阶段后再删除学科");
        }
        long related = subjectMapper.countCoursesBySubject(id)
                + subjectMapper.countRoutesBySubject(id)
                + subjectMapper.countInterviewQuestionsBySubject(id)
                + subjectMapper.countInterviewTopicsBySubject(id);
        if (related > 0) {
            throw new BizException(ErrorCode.HAS_RELATIONS, "该学科仍有关联的课程、路线或面试题，不能删除");
        }
        subjectMapper.deleteById(id);
        operationLogWriter.write("删除", "学科", subject.getName());
    }

    @Transactional
    public void createStage(Long subjectId, StageSaveRequest request) {
        Subject subject = requireSubject(subjectId);
        Stage stage = new Stage();
        stage.setSubjectId(subjectId);
        stage.setStatus(1);
        fillStage(stage, request);
        releaseDeletedStageName(subjectId, stage.getName());
        try {
            stageMapper.insert(stage);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "阶段名称已存在");
        }
        operationLogWriter.write("新增", "阶段", subject.getName() + " / " + stage.getName());
    }

    @Transactional
    public void updateStage(Long subjectId, Long stageId, StageSaveRequest request) {
        Stage stage = requireStage(subjectId, stageId);
        fillStage(stage, request);
        releaseDeletedStageName(subjectId, stage.getName());
        try {
            stageMapper.updateById(stage);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "阶段名称已存在");
        }
        operationLogWriter.write("修改", "阶段", stage.getName());
    }

    @Transactional
    public void sortStages(Long subjectId, SortRequest request) {
        requireSubject(subjectId);
        move(orderedStages(subjectId), request, "阶段");
    }

    @Transactional
    public void updateStageStatus(Long subjectId, Long stageId, StageStatusRequest request) {
        Stage stage = requireStage(subjectId, stageId);
        stage.setStatus(request.status());
        stageMapper.updateById(stage);
        operationLogWriter.write("禁用启用", "阶段", stage.getName());
    }

    @Transactional
    public void deleteStage(Long subjectId, Long stageId) {
        Stage stage = requireStage(subjectId, stageId);
        long questions = stageMapper.countQuestions(stageId);
        if (questions > 0) {
            throw new BizException(ErrorCode.HAS_RELATIONS, "该阶段下有 " + questions + " 道题目，不能删除");
        }
        // 唯一键不含删除标记，逻辑删除会占住名称，导致同名阶段无法再建。
        jdbcTemplate.update("DELETE FROM stage WHERE id = ? AND subject_id = ?", stageId, subjectId);
        operationLogWriter.write("删除", "阶段", stage.getName());
    }

    private void releaseDeletedStageName(Long subjectId, String name) {
        jdbcTemplate.update("DELETE FROM stage WHERE subject_id = ? AND name = ? AND delete_flag = 1", subjectId, name);
    }

    private void fill(Subject subject, SubjectSaveRequest request) {
        subject.setName(request.name().trim());
        subject.setIcon(request.icon().trim());
        subject.setDescription(request.description().trim());
        subject.setSortOrder(request.sortOrder());
        subject.setThemeColor(request.themeColor());
    }

    private void fillStage(Stage stage, StageSaveRequest request) {
        String icon = request.icon() == null ? "" : request.icon().trim();
        stage.setIcon(icon.isEmpty() ? null : icon);
        stage.setName(request.name().trim());
        stage.setSortOrder(request.sortOrder());
    }

    private AdminSubjectView toView(Subject subject) {
        List<AdminStageView> stages = orderedStages(subject.getId()).stream()
                .map(stage -> new AdminStageView(
                        stage.getId(),
                        stage.getName(),
                        stage.getIcon(),
                        stage.getSortOrder() == null ? 0 : stage.getSortOrder(),
                        stage.getStatus() == null ? 1 : stage.getStatus(),
                        stageMapper.countQuestions(stage.getId())))
                .toList();
        return new AdminSubjectView(
                subject.getId(),
                subject.getName(),
                subject.getIcon(),
                subject.getDescription(),
                subject.getThemeColor(),
                subject.getSortOrder() == null ? 0 : subject.getSortOrder(),
                subjectMapper.countCoursesBySubject(subject.getId()),
                subjectMapper.countRoutesBySubject(subject.getId()),
                subjectMapper.countQuestionsBySubject(subject.getId()),
                stages);
    }

    private List<Subject> orderedSubjects() {
        return subjectMapper.selectList(Wrappers.<Subject>lambdaQuery()
                .orderByAsc(Subject::getSortOrder)
                .orderByAsc(Subject::getId));
    }

    private List<Stage> orderedStages(Long subjectId) {
        return stageMapper.selectList(Wrappers.<Stage>lambdaQuery()
                .eq(Stage::getSubjectId, subjectId)
                .orderByAsc(Stage::getSortOrder)
                .orderByAsc(Stage::getId));
    }

    private Subject requireSubject(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "学科不存在");
        }
        return subject;
    }

    private Stage requireStage(Long subjectId, Long stageId) {
        Stage stage = stageMapper.selectById(stageId);
        if (stage == null || !subjectId.equals(stage.getSubjectId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "阶段不存在");
        }
        return stage;
    }

    private void move(List<?> ordered, SortRequest request, String targetType) {
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (idOf(ordered.get(i)).equals(request.id())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new BizException(ErrorCode.NOT_FOUND, targetType + "不存在");
        }
        int target = "up".equals(request.move()) ? index - 1 : index + 1;
        if (target < 0 || target >= ordered.size()) {
            return;
        }
        swapSort(ordered.get(index), ordered.get(target), "up".equals(request.move()));
        operationLogWriter.write("修改", targetType, nameOf(ordered.get(index)));
    }

    private void swapSort(Object current, Object neighbor, boolean up) {
        int currentOrder = sortOf(current);
        int neighborOrder = sortOf(neighbor);
        if (currentOrder == neighborOrder) {
            setSort(current, up ? neighborOrder - 1 : neighborOrder + 1);
        } else {
            setSort(current, neighborOrder);
            setSort(neighbor, currentOrder);
        }
        save(current);
        save(neighbor);
    }

    private void save(Object row) {
        if (row instanceof Subject subject) {
            subjectMapper.updateById(subject);
        } else if (row instanceof Stage stage) {
            stageMapper.updateById(stage);
        }
    }

    private Long idOf(Object row) {
        return row instanceof Subject subject ? subject.getId() : ((Stage) row).getId();
    }

    private int sortOf(Object row) {
        Integer value = row instanceof Subject subject ? subject.getSortOrder() : ((Stage) row).getSortOrder();
        return value == null ? 0 : value;
    }

    private void setSort(Object row, int sortOrder) {
        if (row instanceof Subject subject) {
            subject.setSortOrder(sortOrder);
        } else {
            ((Stage) row).setSortOrder(sortOrder);
        }
    }

    private String nameOf(Object row) {
        return row instanceof Subject subject ? subject.getName() : ((Stage) row).getName();
    }
}
