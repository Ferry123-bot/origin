package com.yunshu.zhixue.modules.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.zhixue.modules.interview.dto.InterviewRecordRow;
import com.yunshu.zhixue.modules.interview.dto.InterviewStatsRow;
import com.yunshu.zhixue.modules.interview.entity.InterviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InterviewRecordMapper extends BaseMapper<InterviewRecord> {

    @Select("""
            SELECT overall_score
            FROM interview_record
            WHERE delete_flag = 0
              AND student_id = #{studentId}
              AND (
                completed_at < #{completedAt}
                OR (completed_at = #{completedAt} AND id < #{id})
              )
            ORDER BY completed_at DESC, id DESC
            LIMIT 1
            """)
    BigDecimal previousScore(@Param("studentId") long studentId, @Param("completedAt") LocalDateTime completedAt,
                             @Param("id") long id);

    @Select("""
            SELECT COUNT(*)
            FROM interview_record
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    long countByStudent(@Param("studentId") long studentId);

    @Select("""
            SELECT r.id, r.subject_id, IFNULL(s.name, '未知方向') AS subject_name,
                   r.difficulty, r.question_count, r.overall_score,
                   IFNULL(r.total_duration, 0) AS total_duration, r.completed_at, p.id AS report_id
            FROM interview_record r
            LEFT JOIN subject s ON s.id = r.subject_id AND s.delete_flag = 0
            LEFT JOIN interview_report p ON p.interview_record_id = r.id AND p.delete_flag = 0
            WHERE r.delete_flag = 0 AND r.student_id = #{studentId}
            ORDER BY r.completed_at DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<InterviewRecordRow> pageByStudent(@Param("studentId") long studentId, @Param("limit") long limit,
                                           @Param("offset") long offset);

    @Select("""
            SELECT CAST(COUNT(*) AS SIGNED) AS interview_total,
                   ROUND(AVG(overall_score), 1) AS avg_score,
                   MAX(overall_score) AS max_score,
                   CAST(COALESCE(SUM(total_duration), 0) AS SIGNED) AS total_duration
            FROM interview_record
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    InterviewStatsRow stats(@Param("studentId") long studentId);
}
