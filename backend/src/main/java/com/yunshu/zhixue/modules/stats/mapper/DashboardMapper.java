package com.yunshu.zhixue.modules.stats.mapper;

import com.yunshu.zhixue.modules.stats.dto.DayCountRow;
import com.yunshu.zhixue.modules.stats.dto.OverviewRow;
import com.yunshu.zhixue.modules.stats.dto.SubjectQuestionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM subject WHERE delete_flag = 0) AS subjectTotal,
              (SELECT COUNT(*) FROM subject WHERE delete_flag = 0 AND created_at >= #{todayStart} AND created_at < #{tomorrowStart}) AS subjectToday,
              (SELECT COUNT(*) FROM subject WHERE delete_flag = 0 AND created_at >= #{yesterdayStart} AND created_at < #{todayStart}) AS subjectYesterday,
              (SELECT COUNT(*) FROM question WHERE delete_flag = 0) AS questionTotal,
              (SELECT COUNT(*) FROM question WHERE delete_flag = 0 AND created_at >= #{todayStart} AND created_at < #{tomorrowStart}) AS questionToday,
              (SELECT COUNT(*) FROM question WHERE delete_flag = 0 AND created_at >= #{yesterdayStart} AND created_at < #{todayStart}) AS questionYesterday,
              (SELECT COUNT(*) FROM student WHERE delete_flag = 0) AS studentTotal,
              (SELECT COUNT(*) FROM student WHERE delete_flag = 0 AND created_at >= #{todayStart} AND created_at < #{tomorrowStart}) AS studentToday,
              (SELECT COUNT(*) FROM student WHERE delete_flag = 0 AND created_at >= #{yesterdayStart} AND created_at < #{todayStart}) AS studentYesterday,
              (SELECT COUNT(*) FROM interview_record WHERE delete_flag = 0) AS interviewTotal,
              (SELECT COUNT(*) FROM interview_record WHERE delete_flag = 0 AND completed_at >= #{todayStart} AND completed_at < #{tomorrowStart}) AS interviewToday,
              (SELECT COUNT(*) FROM interview_record WHERE delete_flag = 0 AND completed_at >= #{yesterdayStart} AND completed_at < #{todayStart}) AS interviewYesterday
            """)
    OverviewRow selectOverview(@Param("yesterdayStart") LocalDateTime yesterdayStart,
                               @Param("todayStart") LocalDateTime todayStart,
                               @Param("tomorrowStart") LocalDateTime tomorrowStart);

    @Select("""
            SELECT DATE_FORMAT(created_at + INTERVAL 8 HOUR, '%Y-%m-%d') AS day, COUNT(*) AS total
            FROM student
            WHERE delete_flag = 0
              AND created_at >= #{start}
              AND created_at < #{end}
            GROUP BY DATE_FORMAT(created_at + INTERVAL 8 HOUR, '%Y-%m-%d')
            ORDER BY day
            """)
    List<DayCountRow> selectRegistrations(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT s.id AS subjectId, s.name AS name, s.theme_color AS themeColor, COUNT(q.id) AS questionCount
            FROM subject s
            LEFT JOIN stage st ON st.subject_id = s.id AND st.delete_flag = 0
            LEFT JOIN question q ON q.stage_id = st.id AND q.delete_flag = 0
            WHERE s.delete_flag = 0
            GROUP BY s.id, s.name, s.theme_color, s.sort_order
            ORDER BY s.sort_order ASC, s.id ASC
            """)
    List<SubjectQuestionRow> selectDistribution();

    @Select("""
            SELECT COUNT(*)
            FROM question q
            LEFT JOIN stage st ON st.id = q.stage_id AND st.delete_flag = 0
            LEFT JOIN subject s ON s.id = st.subject_id AND s.delete_flag = 0
            WHERE q.delete_flag = 0 AND s.id IS NULL
            """)
    long countUnassignedQuestions();
}
