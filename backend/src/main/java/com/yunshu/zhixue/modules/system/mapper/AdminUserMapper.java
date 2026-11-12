package com.yunshu.zhixue.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.zhixue.modules.system.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    @Select("SELECT code FROM role WHERE id = #{roleId} AND delete_flag = 0")
    String findRoleCode(@Param("roleId") Long roleId);

    @Select("""
            SELECT p.code
            FROM permission p
            INNER JOIN role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId} AND p.delete_flag = 0
            ORDER BY p.sort_order, p.id
            """)
    List<String> findPermissionCodes(@Param("roleId") Long roleId);
}
