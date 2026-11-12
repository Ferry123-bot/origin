package com.yunshu.zhixue.modules.interviewbank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewRow;
import com.yunshu.zhixue.modules.interviewbank.dto.TopicCountRow;
import com.yunshu.zhixue.modules.interviewbank.entity.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {

    String LIST_FROM = """
            FROM interview_question q
            INNER JOIN subject sub ON sub.id = q.subject_id AND sub.delete_flag = 0
            WHERE q.delete_flag = 0
            """;

    String FILTERS = """
            <if test="keyword != null and keyword != ''">
              AND q.content LIKE #{keyword} ESCAPE '|'
            </if>
            <if test="subjectId != null">AND q.subject_id = #{subjectId}</if>
            <if test="unclassified">
              AND (q.topic IS NULL OR q.topic = '')
            </if>
            <if test="!unclassified and topicName != null and topicName != ''">
              AND q.topic = #{topicName}
            </if>
            <if test="!unclassified and topicSubjectId != null">
              AND q.subject_id = #{topicSubjectId}
            </if>
            <if test="difficulty != null">AND q.difficulty = #{difficulty}</if>
            """;

    @Select("""
            <script>
            SELECT q.id, q.subject_id, sub.name AS subject_name, q.content, q.reference_answer,
                   q.topic, q.difficulty, q.tags, q.source_type, q.source_doc_name, q.source_seq,
                   q.status, q.created_at, q.updated_at
            """ + LIST_FROM + FILTERS + """
            ORDER BY q.created_at DESC, q.id DESC
            </script>
            """)
    Page<InterviewRow> search(Page<InterviewRow> page, @Param("keyword") String keyword, @Param("subjectId") Long subjectId,
                              @Param("unclassified") boolean unclassified, @Param("topicName") String topicName,
                              @Param("topicSubjectId") Long topicSubjectId, @Param("difficulty") Integer difficulty);

    @Select("""
            <script>
            SELECT q.id, q.subject_id, sub.name AS subject_name, q.content, q.reference_answer,
                   q.topic, q.difficulty, q.tags, q.source_type, q.source_doc_name, q.source_seq,
                   q.status, q.created_at, q.updated_at
            """ + LIST_FROM + """
            AND (
              q.content LIKE #{keyword} ESCAPE '|'
              OR q.reference_answer LIKE #{keyword} ESCAPE '|'
              OR q.topic LIKE #{keyword} ESCAPE '|'
              OR q.tags LIKE #{keyword} ESCAPE '|'
            )
            ORDER BY q.id DESC
            </script>
            """)
    Page<InterviewRow> searchHits(Page<InterviewRow> page, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT topic, subject_id, COUNT(*) AS question_count
            FROM interview_question
            WHERE delete_flag = 0 AND topic IS NOT NULL AND topic &lt;&gt; ''
            <if test="subjectId != null">AND subject_id = #{subjectId}</if>
            GROUP BY topic, subject_id
            </script>
            """)
    List<TopicCountRow> countByTopic(@Param("subjectId") Long subjectId);

    @Select("SELECT COUNT(*) FROM interview_question_score WHERE delete_flag = 0 AND question_id = #{questionId}")
    long countScores(@Param("questionId") Long questionId);

    @Select("""
            SELECT id, subject_id, content, reference_answer, difficulty
            FROM interview_question
            WHERE delete_flag = 0 AND status = 1 AND subject_id = #{subjectId} AND difficulty = #{difficulty}
            ORDER BY RAND()
            LIMIT #{limit}
            """)
    List<InterviewQuestion> drawPublished(@Param("subjectId") Long subjectId, @Param("difficulty") Integer difficulty,
                                          @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM interview_question
            WHERE delete_flag = 0 AND topic = #{name}
            <if test="subjectId != null">AND subject_id = #{subjectId}</if>
            </script>
            """)
    long countUsingTopic(@Param("name") String name, @Param("subjectId") Long subjectId);

    @Update("""
            <script>
            UPDATE interview_question
            SET topic = #{newName}, updated_at = UTC_TIMESTAMP()
            WHERE delete_flag = 0 AND topic = #{oldName}
            <if test="subjectId != null">AND subject_id = #{subjectId}</if>
            </script>
            """)
    int renameTopic(@Param("oldName") String oldName, @Param("newName") String newName, @Param("subjectId") Long subjectId);
}
