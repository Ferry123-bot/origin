package com.yunshu.zhixue.modules.route.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.course.entity.CourseSubject;
import com.yunshu.zhixue.modules.course.mapper.CourseSubjectMapper;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.PortalCourseItem;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.PortalRouteDetail;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.PortalRouteView;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteProgress;
import com.yunshu.zhixue.modules.route.dto.RouteRows.CourseLink;
import com.yunshu.zhixue.modules.route.dto.RouteRows.ProgressCount;
import com.yunshu.zhixue.modules.route.entity.LearningRoute;
import com.yunshu.zhixue.modules.route.entity.RouteCourse;
import com.yunshu.zhixue.modules.route.entity.RouteCourseCompletion;
import com.yunshu.zhixue.modules.route.mapper.LearningRouteMapper;
import com.yunshu.zhixue.modules.route.mapper.RouteCourseCompletionMapper;
import com.yunshu.zhixue.modules.route.mapper.RouteCourseMapper;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoutePortalService {

    private final LearningRouteMapper learningRouteMapper;
    private final RouteCourseMapper routeCourseMapper;
    private final RouteCourseCompletionMapper completionMapper;
    private final CourseSubjectMapper courseSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final StudentMapper studentMapper;
    private final JdbcTemplate jdbcTemplate;

    public RoutePortalService(LearningRouteMapper learningRouteMapper, RouteCourseMapper routeCourseMapper,
                               RouteCourseCompletionMapper completionMapper, CourseSubjectMapper courseSubjectMapper,
                               SubjectMapper subjectMapper, StudentMapper studentMapper, JdbcTemplate jdbcTemplate) {
        this.learningRouteMapper = learningRouteMapper;
        this.routeCourseMapper = routeCourseMapper;
        this.completionMapper = completionMapper;
        this.courseSubjectMapper = courseSubjectMapper;
        this.subjectMapper = subjectMapper;
        this.studentMapper = studentMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PortalRouteView> list() {
        long studentId = requireStudent();
        Map<Long, String> subjectNames = subjectNames();
        Map<Long, Integer> courseCounts = new HashMap<>();
        for (CourseLink link : routeCourseMapper.listActive()) {
            courseCounts.merge(link.getRouteId(), 1, Integer::sum);
        }
        Map<Long, Integer> completed = completedCounts(studentId);
        List<PortalRouteView> views = new ArrayList<>();
        List<LearningRoute> routes = learningRouteMapper.selectList(Wrappers.<LearningRoute>lambdaQuery()
                .eq(LearningRoute::getStatus, 1)
                .orderByAsc(LearningRoute::getId));
        for (LearningRoute route : routes) {
            int courseCount = courseCounts.getOrDefault(route.getId(), 0);
            int done = completed.getOrDefault(route.getId(), 0);
            views.add(new PortalRouteView(route.getId(), route.getName(), route.getSubjectId(),
                    subjectNames.get(route.getSubjectId()), route.getTargetEducation(), route.getDescription(),
                    courseCount, done, RouteSupport.progress(done, courseCount)));
        }
        return views;
    }

    public PortalRouteDetail detail(Long id) {
        long studentId = requireStudent();
        LearningRoute route = publishedRoute(id);
        List<CourseLink> links = routeCourseMapper.listByRoute(id);
        Set<Long> completedIds = completedRouteCourseIds(studentId, links);
        Set<Long> extraCourseIds = coursesWithSubject(links, route.getSubjectId());
        List<PortalCourseItem> courses = new ArrayList<>();
        int totalMinutes = 0;
        int done = 0;
        for (CourseLink link : links) {
            int minutes = RouteSupport.minutes(link.getDurationMinutes());
            totalMinutes += minutes;
            boolean completed = completedIds.contains(link.getRouteCourseId());
            if (completed) {
                done++;
            }
            boolean cross = !route.getSubjectId().equals(link.getSubjectId()) && !extraCourseIds.contains(link.getCourseId());
            courses.add(new PortalCourseItem(link.getRouteCourseId(), link.getCourseId(), link.getName(), link.getBvid(),
                    minutes, link.getSource(), link.getCoverUrl(),
                    RouteSupport.courseLink(link.getLink(), link.getBvid(), link.getSource()),
                    completed, link.getSortOrder() == null ? 0 : link.getSortOrder(), cross));
        }
        return new PortalRouteDetail(route.getId(), route.getName(), route.getSubjectId(),
                subjectNames().get(route.getSubjectId()), route.getIcon(), route.getTargetEducation(), route.getDescription(),
                courses.size(), totalMinutes, done, RouteSupport.progress(done, courses.size()), courses);
    }

    @Transactional
    public RouteProgress complete(Long routeCourseId, Long courseId) {
        long studentId = requireStudent();
        RouteCourse routeCourse = routeCourseMapper.selectById(routeCourseId);
        if (routeCourse == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "路线课程不存在");
        }
        if (!routeCourse.getCourseId().equals(courseId)) {
            throw new BizException(ErrorCode.CONFLICT, "课程与路线课时不匹配");
        }
        LearningRoute route = publishedRoute(routeCourse.getRouteId());
        try {
            RouteCourseCompletion completion = new RouteCourseCompletion();
            completion.setStudentId(studentId);
            completion.setRouteCourseId(routeCourseId);
            completion.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            completionMapper.insert(completion);
        } catch (DuplicateKeyException ex) {
            jdbcTemplate.update("""
                    UPDATE route_course_completion
                    SET delete_flag = 0, completed_at = ?, updated_at = ?
                    WHERE student_id = ? AND route_course_id = ? AND delete_flag = 1
                    """, LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC), studentId, routeCourseId);
        }
        int courseCount = routeCourseMapper.listByRoute(route.getId()).size();
        int done = (int) completionMapper.countByStudentAndRoute(studentId, route.getId());
        return new RouteProgress(done, courseCount, RouteSupport.progress(done, courseCount));
    }

    private Set<Long> coursesWithSubject(List<CourseLink> links, Long subjectId) {
        if (links.isEmpty()) {
            return Set.of();
        }
        List<Long> courseIds = links.stream().map(CourseLink::getCourseId).distinct().toList();
        return courseSubjectMapper.selectList(Wrappers.<CourseSubject>lambdaQuery()
                        .eq(CourseSubject::getSubjectId, subjectId)
                        .in(CourseSubject::getCourseId, courseIds))
                .stream()
                .map(CourseSubject::getCourseId)
                .collect(Collectors.toSet());
    }

    private Set<Long> completedRouteCourseIds(long studentId, List<CourseLink> links) {
        if (links.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = links.stream().map(CourseLink::getRouteCourseId).toList();
        return completionMapper.selectList(Wrappers.<RouteCourseCompletion>lambdaQuery()
                        .eq(RouteCourseCompletion::getStudentId, studentId)
                        .in(RouteCourseCompletion::getRouteCourseId, ids))
                .stream()
                .map(RouteCourseCompletion::getRouteCourseId)
                .collect(Collectors.toSet());
    }

    private Map<Long, Integer> completedCounts(long studentId) {
        Map<Long, Integer> counts = new HashMap<>();
        for (ProgressCount row : completionMapper.countByStudent(studentId)) {
            counts.put(row.getRouteId(), row.getCompletedCount() == null ? 0 : row.getCompletedCount().intValue());
        }
        return counts;
    }

    private Map<Long, String> subjectNames() {
        Map<Long, String> names = new HashMap<>();
        for (Subject subject : subjectMapper.selectList(null)) {
            names.put(subject.getId(), subject.getName());
        }
        return names;
    }

    private LearningRoute publishedRoute(Long id) {
        LearningRoute route = learningRouteMapper.selectById(id);
        if (route == null || route.getStatus() == null || route.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "学习路线不存在或已下架");
        }
        return route;
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
