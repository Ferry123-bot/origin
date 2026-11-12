package com.yunshu.zhixue.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.PermissionNode;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.PermissionSaveRequest;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.RoleCard;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.RoleCreateRequest;
import com.yunshu.zhixue.modules.system.entity.AdminUser;
import com.yunshu.zhixue.modules.system.entity.Permission;
import com.yunshu.zhixue.modules.system.entity.Role;
import com.yunshu.zhixue.modules.system.entity.RolePermission;
import com.yunshu.zhixue.modules.system.mapper.AdminUserMapper;
import com.yunshu.zhixue.modules.system.mapper.PermissionMapper;
import com.yunshu.zhixue.modules.system.mapper.RoleMapper;
import com.yunshu.zhixue.modules.system.mapper.RolePermissionMapper;
import com.yunshu.zhixue.support.ChangeSummary;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleAdminService {

    private static final String SUPER_ADMIN = "super_admin";

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final AdminUserMapper adminUserMapper;
    private final OperationLogWriter operationLogWriter;

    public RoleAdminService(RoleMapper roleMapper, PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper,
                             AdminUserMapper adminUserMapper, OperationLogWriter operationLogWriter) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.adminUserMapper = adminUserMapper;
        this.operationLogWriter = operationLogWriter;
    }

    public List<RoleCard> list() {
        List<Permission> permissions = allPermissions();
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId)).stream()
                .map(role -> toCard(role, permissions, grantedIds(role.getId())))
                .toList();
    }

    @Transactional
    public void create(RoleCreateRequest request) {
        if (SUPER_ADMIN.equals(request.code())) {
            throw new BizException(ErrorCode.BUILTIN_PROTECTED, "不能创建超级管理员角色");
        }
        Role role = new Role();
        role.setName(request.name().trim());
        role.setCode(request.code().trim());
        role.setDescription(blankToNull(request.description()));
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.NAME_EXISTS, "角色编码已存在");
        }
        operationLogWriter.write("新增", "角色", role.getName());
    }

    @Transactional
    public void delete(Long id) {
        Role role = requireRole(id);
        if (SUPER_ADMIN.equals(role.getCode())) {
            throw new BizException(ErrorCode.BUILTIN_PROTECTED, "超级管理员角色不能删除");
        }
        long users = adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getRoleId, id));
        if (users > 0) {
            throw new BizException(ErrorCode.ROLE_HAS_USERS, "该角色下有 " + users + " 个用户，不能删除");
        }
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
        operationLogWriter.write("删除", "角色", role.getName());
    }

    public List<PermissionNode> tree(Long roleId) {
        requireRole(roleId);
        Set<Long> granted = grantedIds(roleId);
        List<Permission> permissions = allPermissions();
        return permissions.stream()
                .filter(item -> item.getParentId() == null)
                .map(group -> new PermissionNode(
                        group.getId(),
                        group.getName(),
                        group.getCode(),
                        granted.contains(group.getId()),
                        permissions.stream()
                                .filter(item -> group.getId().equals(item.getParentId()))
                                .map(item -> new PermissionNode(item.getId(), item.getName(), item.getCode(), granted.contains(item.getId()), List.of()))
                                .toList()))
                .toList();
    }

    @Transactional
    public void savePermissions(Long roleId, PermissionSaveRequest request) {
        Role role = requireRole(roleId);
        if (SUPER_ADMIN.equals(role.getCode())) {
            throw new BizException(ErrorCode.BUILTIN_PROTECTED, "超级管理员角色的权限不能修改");
        }
        ChangeSummary summary = new ChangeSummary();
        try {
            List<Permission> permissions = allPermissions();
            Set<Long> leaves = new HashSet<>();
            Set<Long> known = new HashSet<>();
            for (Permission permission : permissions) {
                known.add(permission.getId());
                if (permission.getType() != null && permission.getType() == 2) {
                    leaves.add(permission.getId());
                }
            }
            Set<Long> selected = new HashSet<>();
            for (Long id : request.permissionIds()) {
                if (id == null || !leaves.contains(id)) {
                    throw new BizException(ErrorCode.PERMISSION_SAVE_FAILED, "包含无效的权限项");
                }
                selected.add(id);
            }
            Set<Long> before = grantedIds(roleId).stream().filter(leaves::contains).collect(java.util.stream.Collectors.toSet());
            String added = permissions.stream()
                    .filter(item -> selected.contains(item.getId()) && !before.contains(item.getId()))
                    .map(Permission::getName)
                    .collect(java.util.stream.Collectors.joining("、"));
            String removed = permissions.stream()
                    .filter(item -> before.contains(item.getId()) && !selected.contains(item.getId()))
                    .map(Permission::getName)
                    .collect(java.util.stream.Collectors.joining("、"));
            summary = new ChangeSummary().note(added.isEmpty() ? "" : "新增权限 " + added).note(removed.isEmpty() ? "" : "取消权限 " + removed);
            Set<Long> stored = new HashSet<>(selected);
            for (Permission permission : permissions) {
                if (selected.contains(permission.getId()) && permission.getParentId() != null && known.contains(permission.getParentId())) {
                    stored.add(permission.getParentId());
                }
            }
            rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, roleId));
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            for (Long permissionId : stored) {
                RolePermission row = new RolePermission();
                row.setRoleId(roleId);
                row.setPermissionId(permissionId);
                row.setCreatedAt(now);
                rolePermissionMapper.insert(row);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.PERMISSION_SAVE_FAILED);
        }
        operationLogWriter.write("修改", "角色", summary.finish("角色「" + role.getName() + "」的权限"));
    }

    private RoleCard toCard(Role role, List<Permission> permissions, Set<Long> granted) {
        List<Permission> leaves = permissions.stream().filter(item -> item.getType() != null && item.getType() == 2).toList();
        long checked = leaves.stream().filter(item -> granted.contains(item.getId())).count();
        List<String> tags = new ArrayList<>();
        if (leaves.isEmpty() || checked == 0) {
            tags.add("暂无权限");
        } else if (checked == leaves.size()) {
            tags.add("全部权限");
        } else {
            for (Permission leaf : leaves) {
                if (!granted.contains(leaf.getId())) {
                    continue;
                }
                String group = permissions.stream()
                        .filter(item -> item.getId().equals(leaf.getParentId()))
                        .map(Permission::getName)
                        .findFirst()
                        .orElse("");
                tags.add(group.isEmpty() ? leaf.getName() : group + "·" + leaf.getName());
            }
        }
        long users = adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getRoleId, role.getId()));
        return new RoleCard(role.getId(), role.getName(), role.getCode(), role.getDescription(), users, tags, SUPER_ADMIN.equals(role.getCode()));
    }

    private Set<Long> grantedIds(Long roleId) {
        return new HashSet<>(rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, roleId))
                .stream().map(RolePermission::getPermissionId).toList());
    }

    private List<Permission> allPermissions() {
        return permissionMapper.selectList(Wrappers.<Permission>lambdaQuery()
                .orderByAsc(Permission::getSortOrder)
                .orderByAsc(Permission::getId));
    }

    private Role requireRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
