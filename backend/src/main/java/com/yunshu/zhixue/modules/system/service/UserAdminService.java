package com.yunshu.zhixue.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.config.AppProperties;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.BatchIdsRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.BatchStatusRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.LogBrief;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.ResetPasswordView;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.RoleOption;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserCreateRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserDetail;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserListItem;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.UserUpdateRequest;
import com.yunshu.zhixue.modules.system.entity.AdminUser;
import com.yunshu.zhixue.modules.system.entity.OperationLog;
import com.yunshu.zhixue.modules.system.entity.Role;
import com.yunshu.zhixue.modules.system.mapper.AdminUserMapper;
import com.yunshu.zhixue.modules.system.mapper.OperationLogMapper;
import com.yunshu.zhixue.modules.system.mapper.RoleMapper;
import com.yunshu.zhixue.security.CurrentAdmin;
import com.yunshu.zhixue.support.ChangeSummary;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserAdminService {

    private static final String SUPER_ADMIN = "super_admin";

    private final AdminUserMapper adminUserMapper;
    private final RoleMapper roleMapper;
    private final OperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final OperationLogWriter operationLogWriter;

    public UserAdminService(AdminUserMapper adminUserMapper, RoleMapper roleMapper, OperationLogMapper operationLogMapper,
                             PasswordEncoder passwordEncoder, AppProperties properties, OperationLogWriter operationLogWriter) {
        this.adminUserMapper = adminUserMapper;
        this.roleMapper = roleMapper;
        this.operationLogMapper = operationLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.operationLogWriter = operationLogWriter;
    }

    public List<RoleOption> roleOptions() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId)).stream()
                .map(role -> new RoleOption(role.getId(), role.getName(), role.getCode()))
                .toList();
    }

    public PageResult<UserListItem> page(String keyword, Long roleId, Integer status, long page, long pageSize) {
        var query = Wrappers.<AdminUser>lambdaQuery().orderByDesc(AdminUser::getId);
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper.like(AdminUser::getUsername, text).or().like(AdminUser::getMobile, text));
        }
        if (roleId != null) {
            query.eq(AdminUser::getRoleId, roleId);
        }
        if (status != null) {
            query.eq(AdminUser::getStatus, status);
        }
        Page<AdminUser> result = adminUserMapper.selectPage(new Page<>(page, pageSize), query);
        Map<Long, String> roleNames = roleNames();
        List<UserListItem> list = result.getRecords().stream().map(user -> toItem(user, roleNames)).toList();
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    @Transactional
    public void create(UserCreateRequest request) {
        Role role = requireAssignableRole(request.roleId(), false);
        AdminUser user = new AdminUser();
        user.setUsername(request.username().trim());
        user.setMobile(request.mobile().trim());
        user.setEmail(blankToNull(request.email()));
        user.setRoleId(role.getId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(request.status());
        user.setIsBuiltin(0);
        insertUser(user);
        operationLogWriter.write("新增", "用户", user.getUsername());
    }

    @Transactional
    public void update(Long id, UserUpdateRequest request) {
        AdminUser user = requireUser(id);
        boolean builtin = isBuiltin(user);
        if (builtin && request.status() != null && request.status() == 0) {
            throw new BizException(ErrorCode.BUILTIN_PROTECTED, "内置账号不能禁用");
        }
        if (builtin && id.equals(CurrentAdmin.id()) && request.status() == 0) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS, "不能禁用当前登录账号");
        }
        if (!builtin && id.equals(CurrentAdmin.id()) && request.status() == 0) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS, "不能禁用当前登录账号");
        }
        Role role = requireAssignableRole(request.roleId(), builtin);
        if (builtin && !role.getId().equals(user.getRoleId())) {
            throw new BizException(ErrorCode.BUILTIN_PROTECTED, "内置账号的角色不能修改");
        }
        Role previousRole = roleMapper.selectById(user.getRoleId());
        int nextStatus = builtin ? 1 : request.status();
        ChangeSummary summary = new ChangeSummary()
                .change("用户名", user.getUsername(), request.username().trim())
                .change("手机号", user.getMobile(), request.mobile().trim())
                .change("邮箱", user.getEmail(), blankToNull(request.email()))
                .change("角色", previousRole == null ? "" : previousRole.getName(), role.getName())
                .change("状态", user.getStatus() != null && user.getStatus() == 0 ? "已禁用" : "已启用", nextStatus == 0 ? "已禁用" : "已启用");
        if (StringUtils.hasText(request.password())) {
            summary.note("修改了密码");
        }
        user.setUsername(request.username().trim());
        user.setMobile(request.mobile().trim());
        user.setEmail(blankToNull(request.email()));
        user.setRoleId(role.getId());
        user.setStatus(nextStatus);
        if (StringUtils.hasText(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        updateUser(user);
        operationLogWriter.write("修改", "用户", summary.finish("用户「" + request.username().trim() + "」"));
    }

    @Transactional
    public void batchStatus(BatchStatusRequest request) {
        List<AdminUser> users = loadUsers(request.ids());
        long self = CurrentAdmin.id();
        for (AdminUser user : users) {
            if (request.status() == 0 && isBuiltin(user)) {
                throw new BizException(ErrorCode.BUILTIN_PROTECTED, "内置账号不能禁用");
            }
            if (request.status() == 0 && user.getId().equals(self)) {
                throw new BizException(ErrorCode.ILLEGAL_STATUS, "不能禁用当前登录账号");
            }
        }
        for (AdminUser user : users) {
            user.setStatus(request.status());
            adminUserMapper.updateById(user);
        }
        operationLogWriter.write("禁用启用", "用户", users.stream().map(AdminUser::getUsername).collect(Collectors.joining("、")));
    }

    @Transactional
    public void delete(Long id) {
        AdminUser user = requireUser(id);
        assertDeletable(user);
        adminUserMapper.deleteById(id);
        operationLogWriter.write("删除", "用户", user.getUsername());
    }

    @Transactional
    public void batchDelete(BatchIdsRequest request) {
        List<AdminUser> users = loadUsers(request.ids());
        users.forEach(this::assertDeletable);
        for (AdminUser user : users) {
            adminUserMapper.deleteById(user.getId());
        }
        operationLogWriter.write("删除", "用户", users.stream().map(AdminUser::getUsername).collect(Collectors.joining("、")));
    }

    @Transactional
    public ResetPasswordView resetPassword(Long id) {
        AdminUser user = requireUser(id);
        String password = properties.getDefaultPassword();
        if (!StringUtils.hasText(password)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "未配置默认密码");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        adminUserMapper.updateById(user);
        operationLogWriter.write("修改", "用户", "用户「" + user.getUsername() + "」：重置为默认密码");
        return new ResetPasswordView(password);
    }

    public UserDetail detail(Long id) {
        AdminUser user = requireUser(id);
        Map<Long, String> roleNames = roleNames();
        List<LogBrief> logs = operationLogMapper.selectList(Wrappers.<OperationLog>lambdaQuery()
                        .eq(OperationLog::getUserId, id)
                        .orderByDesc(OperationLog::getId)
                        .last("LIMIT 10"))
                .stream()
                .map(log -> new LogBrief(log.getId(), log.getAction(), log.getTargetType(), log.getTargetName(), log.getCreatedAt()))
                .toList();
        return new UserDetail(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getEmail(),
                user.getRoleId(),
                roleNames.getOrDefault(user.getRoleId(), ""),
                user.getStatus() == null ? 1 : user.getStatus(),
                isBuiltin(user),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                logs);
    }

    private void insertUser(AdminUser user) {
        try {
            adminUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw duplicate(ex);
        }
    }

    private void updateUser(AdminUser user) {
        try {
            adminUserMapper.updateById(user);
        } catch (DuplicateKeyException ex) {
            throw duplicate(ex);
        }
    }

    private BizException duplicate(DuplicateKeyException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("mobile")) {
            return new BizException(ErrorCode.MOBILE_EXISTS, "手机号已存在");
        }
        return new BizException(ErrorCode.NAME_EXISTS, "用户名已存在");
    }

    private Role requireAssignableRole(Long roleId, boolean builtin) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if (!builtin && SUPER_ADMIN.equals(role.getCode())) {
            throw new BizException(ErrorCode.BUILTIN_PROTECTED, "不能分配超级管理员角色");
        }
        return role;
    }

    private void assertDeletable(AdminUser user) {
        if (isBuiltin(user)) {
            throw new BizException(ErrorCode.BUILTIN_PROTECTED, "内置账号不能删除");
        }
        if (user.getId().equals(CurrentAdmin.id())) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS, "不能删除当前登录账号");
        }
    }

    private List<AdminUser> loadUsers(List<Long> ids) {
        List<AdminUser> users = adminUserMapper.selectList(Wrappers.<AdminUser>lambdaQuery().in(AdminUser::getId, ids));
        if (users.size() != ids.stream().distinct().count()) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return users;
    }

    private AdminUser requireUser(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private Map<Long, String> roleNames() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery()).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
    }

    private UserListItem toItem(AdminUser user, Map<Long, String> roleNames) {
        return new UserListItem(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getEmail(),
                user.getRoleId(),
                roleNames.getOrDefault(user.getRoleId(), ""),
                user.getStatus() == null ? 1 : user.getStatus(),
                isBuiltin(user),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    private boolean isBuiltin(AdminUser user) {
        return user.getIsBuiltin() != null && user.getIsBuiltin() == 1;
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (!text.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "邮箱格式不正确");
        }
        return text;
    }
}
