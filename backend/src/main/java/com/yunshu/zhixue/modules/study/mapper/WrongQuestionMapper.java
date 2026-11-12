package com.yunshu.zhixue.modules.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.zhixue.modules.study.dto.WrongBookStatsRow;
import com.yunshu.zhixue.modules.study.dto.WrongQuestionRow;
import com.yunshu.zhixue.modules.study.entity.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {

    String FROM = """
            FROM wrong_question w
            INNER JOIN question q ON q.id = w.question_id AND q.delete_flag = 0
            LEFT JOIN stage s ON s.id = q.stage_id AND s.delete_flag = 0
            LEFT JOIN subject sub ON sub.id = s.subject_id AND sub.delete_flag = 0
            WHERE w.delete_flag = 0 AND w.student_id = #{studentId}
            """;

    @Select("""
            SELECT COUNT(*) AS total,
                   CAST(COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS SIGNED) AS to_review_count,
                   CAST(COALESCE(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END), 0) AS SIGNED) AS mastered_count
            FROM wrong_question
            WHERE delete_flag = 0 AND student_id = #{studentId}
            """)
    WrongBookStatsRow stats(@Param("studentId") long studentId);

    @Select("""
            SELECT w.id, w.question_id, q.content, w.error_count
            FROM wrong_question w
            INNER JOIN question q ON q.id = w.question_id AND q.delete_flag = 0
            WHERE w.delete_flag = 0 AND w.student_id = #{studentId} AND w.status = 1
            ORDER BY w.error_count DESC, w.id DESC
            LIMIT 3
            """)
    List<WrongQuestionRow> top3(@Param("studentId") long studentId);

    @Select("""
            <script>
            SELECT COUNT(*)
            """ + FROM + """
            <if test="status != null">AND w.status = #{status}</if>
            <if test="type != null">AND q.type = #{type}</if>
            <if test="since != null">AND w.last_wrong_at &gt;= #{since}</if>
            </script>
            """)
    long countList(@Param("studentId") long studentId, @Param("status") Integer status, @Param("type") Integer type,
                    @Param("since") LocalDateTime since);

    @Select("""
            <script>
            SELECT w.id, w.question_id, q.type, q.content, q.answer, q.analysis,
                   w.error_count, w.corrected_count, w.status, w.last_wrong_at, w.last_answer,
                   sub.name AS subject_name, s.name AS stage_name
            """ + FROM + """
            <if test="status != null">AND w.status = #{status}</if>
            <if test="type != null">AND q.type = #{type}</if>
            <if test="since != null">AND w.last_wrong_at &gt;= #{since}</if>
            ORDER BY
            <choose>
              <when test="sort == 'count'">w.error_count DESC, w.id DESC</when>
              <otherwise>w.last_wrong_at DESC, w.id DESC</otherwise>
            </choose>
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<WrongQuestionRow> list(@Param("studentId") long studentId, @Param("status") Integer status,
                                @Param("type") Integer type, @Param("since") LocalDateTime since,
                                @Param("sort") String sort, @Param("offset") long offset, @Param("pageSize") long pageSize);
}
