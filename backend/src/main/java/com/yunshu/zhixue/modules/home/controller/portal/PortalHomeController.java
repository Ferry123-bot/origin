package com.yunshu.zhixue.modules.home.controller.portal;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.HotStageView;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.LearningOverview;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.PlatformStats;
import com.yunshu.zhixue.modules.home.service.HomePortalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal/v1/home")
public class PortalHomeController {

    private final HomePortalService homePortalService;

    public PortalHomeController(HomePortalService homePortalService) {
        this.homePortalService = homePortalService;
    }

    @GetMapping("/platform-stats")
    public ApiResponse<PlatformStats> platformStats() {
        return ApiResponse.ok(homePortalService.platformStats());
    }

    @GetMapping("/hot-stages")
    public ApiResponse<List<HotStageView>> hotStages() {
        return ApiResponse.ok(homePortalService.hotStages());
    }

    @GetMapping("/learning-overview")
    public ApiResponse<LearningOverview> learningOverview() {
        return ApiResponse.ok(homePortalService.learningOverview());
    }
}
