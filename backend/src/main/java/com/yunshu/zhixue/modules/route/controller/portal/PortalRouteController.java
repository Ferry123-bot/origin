package com.yunshu.zhixue.modules.route.controller.portal;

import com.yunshu.zhixue.common.response.ApiResponse;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.CompleteRequest;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.PortalRouteDetail;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.PortalRouteView;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteProgress;
import com.yunshu.zhixue.modules.route.service.RoutePortalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal/v1")
public class PortalRouteController {

    private final RoutePortalService routePortalService;

    public PortalRouteController(RoutePortalService routePortalService) {
        this.routePortalService = routePortalService;
    }

    @GetMapping("/routes")
    public ApiResponse<List<PortalRouteView>> list() {
        return ApiResponse.ok(routePortalService.list());
    }

    @GetMapping("/routes/{id}")
    public ApiResponse<PortalRouteDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(routePortalService.detail(id));
    }

    @PostMapping("/route-courses/{id}/complete")
    public ApiResponse<RouteProgress> complete(@PathVariable Long id, @Valid @RequestBody CompleteRequest request) {
        return ApiResponse.ok(routePortalService.complete(id, request.courseId()));
    }
}
