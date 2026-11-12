package com.yunshu.zhixue.modules.home.service;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.HotStageView;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.LearningOverview;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.LearningStats;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.PlatformStats;
import com.yunshu.zhixue.modules.home.dto.HomePayloads.TrendPoint;
import com.yunshu.zhixue.modules.home.dto.HomeRows.PracticeDay;
import com.yunshu.zhixue.modules.home.dto.HomeRows.PracticeTotal;
import com.yunshu.zhixue.modules.home.mapper.HomeMapper;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HomePortalService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final HomeMapper homeMapper;
    private final StudentMapper studentMapper;

    public HomePortalService(HomeMapper homeMapper, StudentMapper studentMapper) {
        this.homeMapper = homeMapper;
        this.studentMapper = studentMapper;
    }

    public PlatformStats platformStats() {
        long interviews = homeMapper.countInterviews();
        long good = homeMapper.countGoodInterviews();
        int goodRate = interviews == 0 ? 0 : (int) Math.round(good * 100.0 / interviews);
        return new PlatformStats(
                homeMapper.countStudents(),
                homeMapper.countPublishedQuestions(),
                interviews,
                goodRate);
    }

    public List<HotStageView> hotStages() {
        return homeMapper.hotStages().stream()
                .map(row -> new HotStageView(
                        row.getStageId(),
                        row.getSubjectId(),
                        row.getSubjectName(),
                        row.getStageName(),
                        row.getIcon(),
                        row.getQuestionCount() == null ? 0 : row.getQuestionCount()))
                .toList();
    }

    public LearningOverview learningOverview() {
        long studentId = requireStudent();
        Student student = studentMapper.selectById(studentId);
        PracticeTotal total = homeMapper.practiceTotal(studentId);
        long practiced = total == null || total.getTotalCount() == null ? 0 : total.getTotalCount();
        long correct = total == null || total.getCorrectCount() == null ? 0 : total.getCorrectCount();
        int accuracy = practiced == 0 ? 0 : (int) Math.round(correct * 100.0 / practiced);

        Map<LocalDate, Integer> counts = new HashMap<>();
        for (PracticeDay row : homeMapper.practiceDays(studentId)) {
            if (row.getDay() == null || row.getDay().isBlank()) {
                continue;
            }
            counts.put(LocalDate.parse(row.getDay()), row.getCnt() == null ? 0 : row.getCnt().intValue());
        }

        LocalDate today = LocalDate.now(SHANGHAI);
        List<TrendPoint> week = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            week.add(new TrendPoint(date.toString(), counts.getOrDefault(date, 0)));
        }

        return new LearningOverview(
                new LearningStats(streak(counts.keySet(), today), accuracy, (int) Math.min(practiced, Integer.MAX_VALUE)),
                week,
                counts.getOrDefault(today, 0),
                student != null && student.getGuideFinished() != null && student.getGuideFinished() == 1);
    }

    private int streak(Set<LocalDate> days, LocalDate today) {
        if (days.isEmpty()) {
            return 0;
        }
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        Set<LocalDate> lookup = days instanceof HashSet ? days : new HashSet<>(days);
        while (lookup.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private long requireStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        long studentId;
        try {
            studentId = Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (student.getStatus() == null || student.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        return studentId;
    }
}
