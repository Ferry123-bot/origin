package com.yunshu.zhixue.modules.student.mapper;

import com.yunshu.zhixue.modules.student.dto.ProfileRows.PracticeTotal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProfileMapper {

    @Select("""
            SELECT COUNT(*) AS total_count,
                   CAST(COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS SIGNED) AS correct_count
            FROM practice_record
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    PracticeTotal practiceTotal(@Param("studentId") long studentId);

    @Select("""
            SELECT COUNT(DISTINCT DATE_FORMAT(DATE_ADD(practiced_at, INTERVAL 8 HOUR), '%Y-%m-%d'))
            FROM practice_record
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    long practiceDays(@Param("studentId") long studentId);

    @Select("""
            SELECT COUNT(*) FROM interview_record
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    long interviewCount(@Param("studentId") long studentId);

    @Select("""
            SELECT COUNT(*) FROM wrong_question
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    long wrongCount(@Param("studentId") long studentId);
}
