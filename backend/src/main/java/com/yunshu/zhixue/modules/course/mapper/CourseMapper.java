package com.yunshu.zhixue.modules.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.modules.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    @Select("SELECT COALESCE(SUM(duration_minutes), 0) FROM course WHERE delete_flag = 0")
    long sumDurationMinutes();

    @Select("""
            SELECT COUNT(*) FROM (
                SELECT c.subject_id AS subject_id FROM course c
                WHERE c.delete_flag = 0 AND c.subject_id IS NOT NULL
                UNION
                SELECT cs.subject_id FROM course_subject cs
                INNER JOIN course c ON c.id = cs.course_id AND c.delete_flag = 0
                WHERE cs.delete_flag = 0
            ) linked
            """)
    long countLinkedSubjects();

    @Select("SELECT COUNT(*) FROM route_course WHERE delete_flag = 0 AND course_id = #{courseId}")
    long countRouteRefs(@Param("courseId") Long courseId);

    @Select("""
            <script>
            SELECT c.* FROM course c
            WHERE c.delete_flag = 0
            <if test="keyword != null and keyword != ''">
              AND c.name LIKE #{keyword} ESCAPE '|'
            </if>
            <if test="source != null">
              AND c.source = #{source}
            </if>
            <if test="subjectId != null and '0'.toString().equals(subjectId.toString())">
              AND c.subject_id IS NULL
            </if>
            <if test="subjectId != null and !'0'.toString().equals(subjectId.toString())">
              AND (
                c.subject_id = #{subjectId}
                OR EXISTS (
                  SELECT 1 FROM course_subject cs
                  WHERE cs.course_id = c.id AND cs.delete_flag = 0 AND cs.subject_id = #{subjectId}
                )
              )
            </if>
            ORDER BY c.id DESC
            </script>
            """)
    Page<Course> search(Page<Course> page, @Param("keyword") String keyword,
                        @Param("subjectId") Long subjectId, @Param("source") Integer source);
}
