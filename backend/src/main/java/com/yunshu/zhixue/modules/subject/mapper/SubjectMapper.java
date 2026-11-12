package com.yunshu.zhixue.modules.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubjectMapper extends BaseMapper<Subject> {

    @Select("SELECT COUNT(*) FROM stage WHERE delete_flag = 0")
    long countStages();

    @Select("SELECT COUNT(DISTINCT subject_id) FROM stage WHERE delete_flag = 0")
    long countSubjectsWithStages();

    @Select("SELECT COUNT(*) FROM question WHERE delete_flag = 0")
    long countQuestions();

    @Select("""
            SELECT COUNT(*) FROM question q
            INNER JOIN stage s ON s.id = q.stage_id AND s.delete_flag = 0
            WHERE q.delete_flag = 0 AND s.subject_id = #{subjectId}
            """)
    long countQuestionsBySubject(@Param("subjectId") Long subjectId);

    @Select("""
            SELECT COUNT(DISTINCT c.id) FROM course c
            LEFT JOIN course_subject cs ON cs.course_id = c.id AND cs.delete_flag = 0 AND cs.subject_id = #{subjectId}
            WHERE c.delete_flag = 0 AND (c.subject_id = #{subjectId} OR cs.id IS NOT NULL)
            """)
    long countCoursesBySubject(@Param("subjectId") Long subjectId);

    @Select("SELECT COUNT(*) FROM learning_route WHERE delete_flag = 0 AND subject_id = #{subjectId}")
    long countRoutesBySubject(@Param("subjectId") Long subjectId);

    @Select("SELECT COUNT(*) FROM interview_question WHERE delete_flag = 0 AND subject_id = #{subjectId}")
    long countInterviewQuestionsBySubject(@Param("subjectId") Long subjectId);

    @Select("SELECT COUNT(*) FROM interview_topic WHERE delete_flag = 0 AND subject_id = #{subjectId}")
    long countInterviewTopicsBySubject(@Param("subjectId") Long subjectId);
}
