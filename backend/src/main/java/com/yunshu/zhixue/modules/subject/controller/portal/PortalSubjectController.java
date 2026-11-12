package com.yunshu.zhixue.modules.subject.controller.portal;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.PortalStageView;
import com.yunshu.zhixue.modules.subject.dto.SubjectPayloads.PortalSubjectView;
import com.yunshu.zhixue.modules.subject.service.SubjectPortalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal/v1/subjects")
public class PortalSubjectController {

    private final SubjectPortalService subjectPortalService;

    public PortalSubjectController(SubjectPortalService subjectPortalService) {
        this.subjectPortalService = subjectPortalService;
    }

    @GetMapping
    public ApiResponse<List<PortalSubjectView>> list() {
        return ApiResponse.ok(subjectPortalService.listSubjects());
    }

    @GetMapping("/{subjectId}/stages")
    public ApiResponse<List<PortalStageView>> stages(@PathVariable Long subjectId) {
        return ApiResponse.ok(subjectPortalService.listStages(subjectId));
    }
}
