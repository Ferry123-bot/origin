package com.yunshu.zhixue.modules.question.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.modules.question.dto.PortalQuestionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PortalQuestionMapper {

    String FROM = """
            FROM question q
            INNER JOIN stage s ON s.id = q.stage_id AND s.delete_flag = 0 AND s.status = 1
            INNER JOIN subject sub ON sub.id = s.subject_id AND sub.delete_flag = 0
            WHERE q.delete_flag = 0 AND q.status = 1
            """;

    @Select("""
            <script>
            SELECT q.id, q.type, q.difficulty, q.content, q.tags, sub.name AS subject_name, s.name AS stage_name,
                   (SELECT COUNT(*) FROM practice_record p WHERE p.delete_flag = 0 AND p.question_id = q.id) AS done_count,
                   (SELECT COUNT(*) FROM practice_record p WHERE p.delete_flag = 0 AND p.question_id = q.id AND p.is_correct = 1) AS correct_count
            """ + FROM + """
            <if test="subjectId != null">AND s.subject_id = #{subjectId}</if>
            <if test="stageId != null">AND q.stage_id = #{stageId}</if>
            <if test="type != null">AND q.type = #{type}</if>
            <if test="difficulty != null">AND q.difficulty = #{difficulty}</if>
            <if test="keyword != null and keyword != ''">
              AND q.content LIKE #{keyword} ESCAPE '|'
            </if>
            ORDER BY q.id ASC
            </script>
            """)
    Page<PortalQuestionRow> search(Page<PortalQuestionRow> page, @Param("subjectId") Long subjectId,
                                   @Param("stageId") Long stageId, @Param("type") Integer type,
                                   @Param("difficulty") Integer difficulty, @Param("keyword") String keyword);

    @Select("""
            SELECT q.id, q.type, q.difficulty, q.content, q.answer, q.analysis, q.tags,
                   sub.name AS subject_name, s.name AS stage_name
            """ + FROM + """
            AND q.id = #{id}
            """)
    PortalQuestionRow findPublished(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*) FROM wrong_question
            WHERE delete_flag = 0 AND student_id = #{studentId} AND question_id = #{questionId}
            """)
    long countWrong(@Param("studentId") long studentId, @Param("questionId") long questionId);
}
