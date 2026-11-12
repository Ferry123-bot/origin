package com.yunshu.zhixue.modules.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.zhixue.modules.route.dto.RouteRows.CourseLink;
import com.yunshu.zhixue.modules.route.entity.RouteCourse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RouteCourseMapper extends BaseMapper<RouteCourse> {

    String COURSE_LINK_COLUMNS = """
            SELECT rc.id AS route_course_id, rc.route_id, rc.course_id, rc.sort_order,
                   c.name, c.bvid, c.duration_minutes, c.cover_url, c.link, c.source, c.subject_id
            FROM route_course rc
            INNER JOIN course c ON c.id = rc.course_id AND c.delete_flag = 0
            WHERE rc.delete_flag = 0
            """;

    @Select(COURSE_LINK_COLUMNS + " ORDER BY rc.route_id ASC, rc.sort_order ASC, rc.id ASC")
    List<CourseLink> listActive();

    @Select(COURSE_LINK_COLUMNS + " AND rc.route_id = #{routeId} ORDER BY rc.sort_order ASC, rc.id ASC")
    List<CourseLink> listByRoute(@Param("routeId") Long routeId);
}
