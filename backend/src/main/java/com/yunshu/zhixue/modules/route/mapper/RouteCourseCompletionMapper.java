package com.yunshu.zhixue.modules.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.zhixue.modules.route.dto.RouteRows.ProgressCount;
import com.yunshu.zhixue.modules.route.entity.RouteCourseCompletion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RouteCourseCompletionMapper extends BaseMapper<RouteCourseCompletion> {

    @Select("""
            SELECT rc.route_id AS route_id, COUNT(*) AS completed_count
            FROM route_course_completion c
            INNER JOIN route_course rc ON rc.id = c.route_course_id AND rc.delete_flag = 0
            INNER JOIN course co ON co.id = rc.course_id AND co.delete_flag = 0
            WHERE c.student_id = #{studentId} AND c.delete_flag = 0
            GROUP BY rc.route_id
            """)
    List<ProgressCount> countByStudent(@Param("studentId") Long studentId);

    @Select("""
            SELECT COUNT(*)
            FROM route_course_completion c
            INNER JOIN route_course rc ON rc.id = c.route_course_id AND rc.delete_flag = 0
            INNER JOIN course co ON co.id = rc.course_id AND co.delete_flag = 0
            WHERE c.student_id = #{studentId} AND c.delete_flag = 0 AND rc.route_id = #{routeId}
            """)
    long countByStudentAndRoute(@Param("studentId") Long studentId, @Param("routeId") Long routeId);
}
