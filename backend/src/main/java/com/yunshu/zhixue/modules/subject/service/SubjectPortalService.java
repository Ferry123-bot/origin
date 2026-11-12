package com.yunshu.zhixue.modules.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.PortalStageView;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.PortalSubjectView;
import com.yunshu.zhixue.modules.subject.entity.Stage;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.StageMapper;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectPortalService {

    private final SubjectMapper subjectMapper;
    private final StageMapper stageMapper;

    public SubjectPortalService(SubjectMapper subjectMapper, StageMapper stageMapper) {
        this.subjectMapper = subjectMapper;
        this.stageMapper = stageMapper;
    }

    public List<PortalSubjectView> listSubjects() {
        return subjectMapper.selectList(Wrappers.<Subject>lambdaQuery()
                        .orderByAsc(Subject::getSortOrder)
                        .orderByAsc(Subject::getId))
                .stream()
                .map(subject -> new PortalSubjectView(
                        subject.getId(),
                        subject.getName(),
                        subject.getIcon(),
                        subject.getDescription(),
                        subject.getThemeColor(),
                        stageMapper.selectCount(Wrappers.<Stage>lambdaQuery()
                                .eq(Stage::getSubjectId, subject.getId())
                                .eq(Stage::getStatus, 1)),
                        subject.getSortOrder() == null ? 0 : subject.getSortOrder()))
                .toList();
    }

    public List<PortalStageView> listStages(Long subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "学科不存在");
        }
        return stageMapper.selectList(Wrappers.<Stage>lambdaQuery()
                        .eq(Stage::getSubjectId, subjectId)
                        .eq(Stage::getStatus, 1)
                        .orderByAsc(Stage::getSortOrder)
                        .orderByAsc(Stage::getId))
                .stream()
                .map(stage -> new PortalStageView(
                        stage.getId(),
                        stage.getName(),
                        stage.getIcon(),
                        stageMapper.countPublishedQuestions(stage.getId()),
                        stage.getSortOrder() == null ? 0 : stage.getSortOrder()))
                .toList();
    }
}
