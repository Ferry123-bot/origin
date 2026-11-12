package com.yunshu.zhixue.support;

import com.yunshu.zhixue.common.util.ClientIp;
import com.yunshu.zhixue.modules.system.entity.AdminUser;
import com.yunshu.zhixue.modules.system.entity.OperationLog;
import com.yunshu.zhixue.modules.system.mapper.AdminUserMapper;
import com.yunshu.zhixue.modules.system.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class OperationLogWriter {

    private static final Logger log = LoggerFactory.getLogger(OperationLogWriter.class);

    private final OperationLogMapper operationLogMapper;
    private final AdminUserMapper adminUserMapper;

    public OperationLogWriter(OperationLogMapper operationLogMapper, AdminUserMapper adminUserMapper) {
        this.operationLogMapper = operationLogMapper;
        this.adminUserMapper = adminUserMapper;
    }

    public void write(String action, String targetType, String targetName) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getPrincipal() == null) {
                return;
            }
            long userId = Long.parseLong(authentication.getPrincipal().toString());
            AdminUser user = adminUserMapper.selectById(userId);
            if (user == null) {
                return;
            }
            OperationLog row = new OperationLog();
            row.setUserId(user.getId());
            row.setUsername(user.getUsername());
            row.setAction(action);
            row.setTargetType(targetType);
            row.setTargetName(targetName);
            row.setIp(currentIp());
            row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            operationLogMapper.insert(row);
        } catch (RuntimeException ex) {
            log.warn("写入操作日志失败 action={} target={}", action, targetName);
        }
    }

    private String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "0.0.0.0";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = ClientIp.resolve(request);
        return ip == null || ip.isBlank() ? "0.0.0.0" : ip;
    }
}
