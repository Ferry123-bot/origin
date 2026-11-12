package com.yunshu.zhixue.modules.study.mapper;

import com.yunshu.zhixue.modules.study.dto.PracticeQuestionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PracticeQuestionMapper {

    String FROM = """
            FROM question q
            INNER JOIN stage s ON s.id = q.stage_id AND s.delete_flag = 0 AND s.status = 1
            INNER JOIN subject sub ON sub.id = s.subject_id AND sub.delete_flag = 0
            WHERE q.delete_flag = 0 AND q.status = 1
            """;

    @Select("""
            <script>
            SELECT q.id, q.type, q.difficulty, q.content, q.answer, q.analysis,
                   s.id AS stage_id, s.name AS stage_name, sub.id AS subject_id, sub.name AS subject_name
            """ + FROM + """
            <if test="subjectId != null">AND s.subject_id = #{subjectId}</if>
            <if test="stageId != null">AND q.stage_id = #{stageId}</if>
            <if test="type != null">AND q.type = #{type}</if>
            <if test="difficulty != null">AND q.difficulty = #{difficulty}</if>
            <if test="questionId != null">AND q.id = #{questionId}</if>
            <if test="questionIds != null">
              AND q.id IN
              <foreach collection="questionIds" item="item" open="(" separator="," close=")">#{item}</foreach>
            </if>
            ORDER BY q.id ASC
            </script>
            """)
    List<PracticeQuestionRow> list(@Param("subjectId") Long subjectId, @Param("stageId") Long stageId,
                                   @Param("type") Integer type, @Param("difficulty") Integer difficulty,
                                   @Param("questionId") Long questionId, @Param("questionIds") List<Long> questionIds);
}
