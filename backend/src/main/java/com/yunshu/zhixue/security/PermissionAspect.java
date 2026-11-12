package com.yunshu.zhixue.security;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.system.mapper.AdminUserMapper;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
public class PermissionAspect {

    private final AdminUserMapper adminUserMapper;

    public PermissionAspect(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Before("@annotation(requirePermission)")
    public void check(RequirePermission requirePermission) {
        var user = requireEnabledUser();
        List<String> codes = adminUserMapper.findPermissionCodes(user.getRoleId());
        if (codes == null || !codes.contains(requirePermission.value())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    @Before("@annotation(com.yunshu.zhixue.security.RequireSuperAdmin)")
    public void checkSuperAdmin() {
        var user = requireEnabledUser();
        String code = adminUserMapper.findRoleCode(user.getRoleId());
        if (!"super_admin".equals(code)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private com.yunshu.zhixue.modules.system.entity.AdminUser requireEnabledUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        long userId;
        try {
            userId = Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        var user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        return user;
    }
}
