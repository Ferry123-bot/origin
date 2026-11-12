package com.yunshu.zhixue.modules.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.modules.question.dto.QuestionRow;
import com.yunshu.zhixue.modules.question.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    String LIST_FROM = """
            FROM question q
            INNER JOIN stage s ON s.id = q.stage_id AND s.delete_flag = 0
            INNER JOIN subject sub ON sub.id = s.subject_id AND sub.delete_flag = 0
            WHERE q.delete_flag = 0
            """;

    @Select("SELECT COUNT(*) FROM question WHERE delete_flag = 0")
    long countAll();

    @Select("SELECT COUNT(*) FROM question WHERE delete_flag = 0 AND status = #{status}")
    long countByStatus(@Param("status") int status);

    @Select("SELECT COUNT(*) FROM question WHERE delete_flag = 0 AND type = #{type}")
    long countByType(@Param("type") int type);

    @Select("SELECT COUNT(*) FROM practice_record WHERE delete_flag = 0 AND question_id = #{questionId}")
    long countPractice(@Param("questionId") Long questionId);

    @Select("SELECT COUNT(*) FROM wrong_question WHERE delete_flag = 0 AND question_id = #{questionId}")
    long countWrong(@Param("questionId") Long questionId);

    @Select("SELECT COUNT(*) FROM practice_record WHERE delete_flag = 0 AND question_id = #{questionId} AND is_correct = 1")
    long countCorrect(@Param("questionId") Long questionId);

    @Select("""
            <script>
            SELECT COUNT(*)
            """ + LIST_FROM + """
            <if test="keyword != null and keyword != ''">
              AND q.content LIKE #{keyword} ESCAPE '|'
            </if>
            <if test="subjectId != null">AND s.subject_id = #{subjectId}</if>
            <if test="stageId != null">AND q.stage_id = #{stageId}</if>
            <if test="type != null">AND q.type = #{type}</if>
            <if test="difficulty != null">AND q.difficulty = #{difficulty}</if>
            <if test="status != null">AND q.status = #{status}</if>
            </script>
            """)
    long countFiltered(@Param("keyword") String keyword, @Param("subjectId") Long subjectId, @Param("stageId") Long stageId,
                       @Param("type") Integer type, @Param("difficulty") Integer difficulty, @Param("status") Integer status);

    @Select("""
            <script>
            SELECT q.id, q.stage_id, s.name AS stage_name, sub.id AS subject_id, sub.name AS subject_name,
                   q.type, q.difficulty, q.content, q.answer, q.analysis, q.tags, q.status, q.reject_reason, q.created_at,
                   (SELECT COUNT(*) FROM practice_record p WHERE p.delete_flag = 0 AND p.question_id = q.id) AS done_count,
                   (SELECT COUNT(*) FROM practice_record p WHERE p.delete_flag = 0 AND p.question_id = q.id AND p.is_correct = 1) AS correct_count
            """ + LIST_FROM + """
            <if test="keyword != null and keyword != ''">
              AND q.content LIKE #{keyword} ESCAPE '|'
            </if>
            <if test="subjectId != null">AND s.subject_id = #{subjectId}</if>
            <if test="stageId != null">AND q.stage_id = #{stageId}</if>
            <if test="type != null">AND q.type = #{type}</if>
            <if test="difficulty != null">AND q.difficulty = #{difficulty}</if>
            <if test="status != null">AND q.status = #{status}</if>
            ORDER BY q.id DESC
            </script>
            """)
    Page<QuestionRow> search(Page<QuestionRow> page, @Param("keyword") String keyword, @Param("subjectId") Long subjectId,
                             @Param("stageId") Long stageId, @Param("type") Integer type,
                             @Param("difficulty") Integer difficulty, @Param("status") Integer status);
}
