package com.yunshu.zhixue.modules.stats.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.ActivityItem;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.GrowthPoint;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.Metric;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.Overview;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.QuestionDistribution;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.SubjectSlice;
import com.yunshu.zhixue.modules.stats.dto.DashboardPayloads.UserGrowth;
import com.yunshu.zhixue.modules.stats.dto.DayCountRow;
import com.yunshu.zhixue.modules.stats.dto.OverviewRow;
import com.yunshu.zhixue.modules.stats.dto.SubjectQuestionRow;
import com.yunshu.zhixue.modules.stats.mapper.DashboardMapper;
import com.yunshu.zhixue.modules.system.entity.OperationLog;
import com.yunshu.zhixue.modules.system.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Set<Integer> RANGES = Set.of(7, 30, 90);
    private static final String OTHER_COLOR = "#909399";

    private final DashboardMapper dashboardMapper;
    private final OperationLogMapper operationLogMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DashboardService(DashboardMapper dashboardMapper, OperationLogMapper operationLogMapper,
                            StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.dashboardMapper = dashboardMapper;
        this.operationLogMapper = operationLogMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Overview overview() {
        return cached("dashboard:overview", Overview.class, this::loadOverview);
    }

    public UserGrowth userGrowth(int range) {
        if (!RANGES.contains(range)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择 7、30 或 90 天");
        }
        return cached("dashboard:user-growth:" + range, UserGrowth.class, () -> loadGrowth(range));
    }

    public QuestionDistribution questionDistribution() {
        return cached("dashboard:question-distribution", QuestionDistribution.class, this::loadDistribution);
    }

    public PageResult<ActivityItem> recentActivities(long page, long pageSize) {
        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<OperationLog>lambdaQuery()
                        .orderByDesc(OperationLog::getCreatedAt)
                        .orderByDesc(OperationLog::getId));
        List<ActivityItem> list = result.getRecords().stream().map(this::toActivity).toList();
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    private Overview loadOverview() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI);
        LocalDate today = now.toLocalDate();
        OverviewRow row = dashboardMapper.selectOverview(utc(today.minusDays(1)), utc(today), utc(today.plusDays(1)));
        if (row == null) {
            row = new OverviewRow();
        }
        return new Overview(
                metric(row.getSubjectTotal(), row.getSubjectToday(), row.getSubjectYesterday()),
                metric(row.getQuestionTotal(), row.getQuestionToday(), row.getQuestionYesterday()),
                metric(row.getStudentTotal(), row.getStudentToday(), row.getStudentYesterday()),
                metric(row.getInterviewTotal(), row.getInterviewToday(), row.getInterviewYesterday()));
    }

    private UserGrowth loadGrowth(int range) {
        LocalDate today = ZonedDateTime.now(SHANGHAI).toLocalDate();
        LocalDate start = today.minusDays(range - 1L);
        Map<String, Long> counts = new HashMap<>();
        for (DayCountRow row : dashboardMapper.selectRegistrations(utc(start), utc(today.plusDays(1)))) {
            if (row.getDay() != null) {
                counts.put(row.getDay(), value(row.getTotal()));
            }
        }
        List<GrowthPoint> points = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            String date = day.toString();
            points.add(new GrowthPoint(date, counts.getOrDefault(date, 0L)));
        }
        return new UserGrowth(range, points);
    }

    private QuestionDistribution loadDistribution() {
        List<SubjectSlice> items = new ArrayList<>();
        for (SubjectQuestionRow row : dashboardMapper.selectDistribution()) {
            items.add(new SubjectSlice(row.getSubjectId(), row.getName() == null ? "" : row.getName(),
                    row.getThemeColor(), value(row.getQuestionCount())));
        }
        long others = dashboardMapper.countUnassignedQuestions();
        if (others > 0) {
            items.add(new SubjectSlice(null, "其他", OTHER_COLOR, others));
        }
        return new QuestionDistribution(items);
    }

    private ActivityItem toActivity(OperationLog row) {
        String detail = text(row.getTargetName());
        if (detail.isEmpty()) {
            detail = text(row.getTargetType());
        }
        return new ActivityItem(row.getId() == null ? 0 : row.getId(), text(row.getUsername()),
                text(row.getAction()), detail, row.getCreatedAt());
    }

    private Metric metric(Long total, Long today, Long yesterday) {
        long todayCount = value(today);
        long yesterdayCount = value(yesterday);
        Double rate = yesterdayCount == 0 ? null : Math.round((todayCount - yesterdayCount) * 1000.0 / yesterdayCount) / 10.0;
        return new Metric(value(total), todayCount, yesterdayCount, rate);
    }

    private LocalDateTime utc(LocalDate day) {
        return day.atStartOfDay(SHANGHAI).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private long value(Long number) {
        return number == null ? 0 : number;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> T cached(String key, Class<T> type, java.util.function.Supplier<T> loader) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, type);
            }
        } catch (Exception ex) {
            log.warn("读取看板缓存失败：{}", ex.toString());
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), CACHE_TTL);
        } catch (Exception ex) {
            log.warn("写入看板缓存失败：{}", ex.toString());
        }
        return value;
    }
}
