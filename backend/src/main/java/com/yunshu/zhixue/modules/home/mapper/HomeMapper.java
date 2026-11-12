package com.yunshu.zhixue.modules.home.mapper;

import com.yunshu.zhixue.modules.home.dto.HomeRows.HotStageRow;
import com.yunshu.zhixue.modules.home.dto.HomeRows.PracticeDay;
import com.yunshu.zhixue.modules.home.dto.HomeRows.PracticeTotal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeMapper {

    @Select("""
            SELECT COUNT(*) FROM question
            WHERE delete_flag = 0 AND status = 1
            """)
    long countPublishedQuestions();

    @Select("""
            SELECT COUNT(*) FROM student
            WHERE delete_flag = 0
            """)
    long countStudents();

    @Select("""
            SELECT COUNT(*) FROM interview_record
            WHERE delete_flag = 0
            """)
    long countInterviews();

    @Select("""
            SELECT COUNT(*) FROM interview_record
            WHERE delete_flag = 0 AND overall_score >= 8
            """)
    long countGoodInterviews();

    @Select("""
            SELECT st.id AS stage_id,
                   st.subject_id AS subject_id,
                   su.name AS subject_name,
                   st.name AS stage_name,
                   st.icon AS icon,
                   (SELECT COUNT(*) FROM question q
                     WHERE q.delete_flag = 0 AND q.status = 1 AND q.stage_id = st.id) AS question_count
            FROM stage st
            INNER JOIN subject su ON su.id = st.subject_id AND su.delete_flag = 0
            WHERE st.delete_flag = 0 AND st.status = 1
            ORDER BY question_count DESC, st.sort_order ASC, st.id ASC
            LIMIT 8
            """)
    List<HotStageRow> hotStages();

    @Select("""
            SELECT COUNT(*) AS total_count,
                   CAST(COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS SIGNED) AS correct_count
            FROM practice_record
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    PracticeTotal practiceTotal(@Param("studentId") long studentId);

    @Select("""
            SELECT DATE_FORMAT(DATE_ADD(practiced_at, INTERVAL 8 HOUR), '%Y-%m-%d') AS day,
                   COUNT(*) AS cnt
            FROM practice_record
            WHERE delete_flag = 0 AND student_id = #{studentId}
            GROUP BY DATE_FORMAT(DATE_ADD(practiced_at, INTERVAL 8 HOUR), '%Y-%m-%d')
            """)
    List<PracticeDay> practiceDays(@Param("studentId") long studentId);
}
