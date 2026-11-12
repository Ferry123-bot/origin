package com.yunshu.zhixue.modules.route.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.course.entity.Course;
import com.yunshu.zhixue.modules.course.entity.CourseSubject;
import com.yunshu.zhixue.modules.course.mapper.CourseMapper;
import com.yunshu.zhixue.modules.course.mapper.CourseSubjectMapper;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.AddCoursesResult;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.CourseOption;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.CoursePreview;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteBoard;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteCard;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteCourseItem;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteDetail;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.RouteSaveRequest;
import com.yunshu.zhixue.modules.route.dto.RoutePayloads.SubjectOption;
import com.yunshu.zhixue.modules.route.dto.RouteRows.CourseLink;
import com.yunshu.zhixue.modules.route.entity.LearningRoute;
import com.yunshu.zhixue.modules.route.entity.RouteCourse;
import com.yunshu.zhixue.modules.route.mapper.LearningRouteMapper;
import com.yunshu.zhixue.modules.route.mapper.RouteCourseMapper;
import com.yunshu.zhixue.modules.subject.entity.Subject;
import com.yunshu.zhixue.modules.subject.mapper.SubjectMapper;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RouteAdminService {

    private final LearningRouteMapper learningRouteMapper;
    private final RouteCourseMapper routeCourseMapper;
    private final CourseMapper courseMapper;
    private final CourseSubjectMapper courseSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OperationLogWriter operationLogWriter;

    public RouteAdminService(LearningRouteMapper learningRouteMapper, RouteCourseMapper routeCourseMapper,
                              CourseMapper courseMapper, CourseSubjectMapper courseSubjectMapper,
                              SubjectMapper subjectMapper, JdbcTemplate jdbcTemplate,
                              OperationLogWriter operationLogWriter) {
        this.learningRouteMapper = learningRouteMapper;
        this.routeCourseMapper = routeCourseMapper;
        this.courseMapper = courseMapper;
        this.courseSubjectMapper = courseSubjectMapper;
        this.subjectMapper = subjectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.operationLogWriter = operationLogWriter;
    }

    public RouteBoard board() {
        Map<Long, Subject> subjects = subjectsById();
        Map<Long, List<CourseLink>> links = group(routeCourseMapper.listActive());
        List<RouteCard> cards = new ArrayList<>();
        List<LearningRoute> routes = learningRouteMapper.selectList(Wrappers.<LearningRoute>lambdaQuery()
                .orderByDesc(LearningRoute::getId));
        for (LearningRoute route : routes) {
            List<CourseLink> courses = links.getOrDefault(route.getId(), List.of());
            int totalMinutes = courses.stream().mapToInt(link -> RouteSupport.minutes(link.getDurationMinutes())).sum();
            List<CoursePreview> previews = courses.stream().limit(4)
                    .map(link -> new CoursePreview(link.getCourseId(), link.getName(), link.getCoverUrl(), link.getBvid(),
                            RouteSupport.minutes(link.getDurationMinutes())))
                    .toList();
            Subject subject = subjects.get(route.getSubjectId());
            cards.add(new RouteCard(route.getId(), route.getName(), route.getStatus(), route.getSubjectId(),
                    subject == null ? null : subject.getName(), subject == null ? null : subject.getIcon(),
                    route.getIcon(), route.getTargetEducation(), route.getDescription(), courses.size(), totalMinutes,
                    RouteSupport.durationText(totalMinutes), previews));
        }
        List<SubjectOption> options = subjects.values().stream()
                .sorted(Comparator.comparing(Subject::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Subject::getId))
                .map(subject -> new SubjectOption(subject.getId(), subject.getName(), subject.getIcon(), subject.getThemeColor()))
                .toList();
        return new RouteBoard(options, cards);
    }

    public RouteDetail detail(Long id) {
        LearningRoute route = requireRoute(id);
        return toDetail(route);
    }

    public List<CourseOption> courseOptions(Long subjectId) {
        requireSubject(subjectId);
        Map<Long, String> names = new HashMap<>();
        for (Subject subject : subjectMapper.selectList(null)) {
            names.put(subject.getId(), subject.getName());
        }
        Map<Long, Set<Long>> extras = extrasByCourse(null);
        List<CourseOption> options = new ArrayList<>();
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery().orderByAsc(Course::getName));
        for (Course course : courses) {
            int minutes = RouteSupport.minutes(course.getDurationMinutes());
            boolean own = belongs(course.getSubjectId(), extras.getOrDefault(course.getId(), Set.of()), subjectId);
            String subjectName = course.getSubjectId() == null ? "其他学科" : names.get(course.getSubjectId());
            options.add(new CourseOption(course.getId(), course.getName(), course.getBvid(), minutes,
                    RouteSupport.durationText(minutes), course.getCoverUrl(), course.getSubjectId(), subjectName, own));
        }
        options.sort(Comparator.comparingInt((CourseOption option) -> option.own() ? 0 : 1)
                .thenComparing(CourseOption::name, Comparator.nullsLast(String::compareTo)));
        return options;
    }

    @Transactional
    public void create(RouteSaveRequest request) {
        Subject subject = requireSubject(request.subjectId());
        String name = request.name().trim();
        List<Long> courseIds = request.courseIds() == null ? List.of() : orderedIds(request.courseIds(), false);
        assertCoursesExist(courseIds);
        LearningRoute route = new LearningRoute();
        route.setName(name);
        route.setSubjectId(subject.getId());
        route.setStatus(request.status() == null ? 1 : request.status());
        route.setIcon(blankToNull(request.icon()));
        route.setTargetEducation(blankToNull(request.targetEducation()));
        route.setDescription(blankToNull(request.description()));
        learningRouteMapper.insert(route);
        syncCourses(route.getId(), courseIds);
        operationLogWriter.write("新增", "学习路线", name);
    }

    @Transactional
    public void update(Long id, RouteSaveRequest request) {
        LearningRoute current = requireRoute(id);
        requireSubject(request.subjectId());
        String name = request.name().trim();
        int status = request.status() == null ? current.getStatus() : request.status();
        List<Long> courseIds = null;
        if (request.courseIds() != null) {
            courseIds = orderedIds(request.courseIds(), false);
            assertCoursesExist(courseIds);
        }
        int updated = jdbcTemplate.update("""
                UPDATE learning_route
                SET name = ?, subject_id = ?, status = ?, icon = ?, target_education = ?, description = ?, updated_at = ?
                WHERE id = ? AND delete_flag = 0
                """, name, request.subjectId(), status, blankToNull(request.icon()),
                blankToNull(request.targetEducation()), blankToNull(request.description()),
                LocalDateTime.now(ZoneOffset.UTC), id);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "学习路线不存在");
        }
        if (courseIds != null) {
            syncCourses(id, courseIds);
        }
        operationLogWriter.write("修改", "学习路线", name);
    }

    @Transactional
    public void changeStatus(Long id, int status) {
        LearningRoute route = requireRoute(id);
        int updated = jdbcTemplate.update(
                "UPDATE learning_route SET status = ?, updated_at = ? WHERE id = ? AND delete_flag = 0",
                status, LocalDateTime.now(ZoneOffset.UTC), id);
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "学习路线不存在");
        }
        operationLogWriter.write("禁用启用", "学习路线", route.getName());
    }

    @Transactional
    public void delete(Long id) {
        LearningRoute route = requireRoute(id);
        List<Long> routeCourseIds = jdbcTemplate.queryForList(
                "SELECT id FROM route_course WHERE route_id = ?", Long.class, id);
        purgeRouteCourses(routeCourseIds);
        learningRouteMapper.deleteById(id);
        operationLogWriter.write("删除", "学习路线", route.getName());
    }

    @Transactional
    public AddCoursesResult addCourses(Long id, List<Long> courseIds) {
        LearningRoute route = requireRoute(id);
        List<Long> desired = orderedIds(courseIds, false);
        assertCoursesExist(desired);
        Set<Long> existing = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT course_id FROM route_course WHERE route_id = ? AND delete_flag = 0", Long.class, id));
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM route_course WHERE route_id = ? AND delete_flag = 0",
                Integer.class, id);
        int order = max == null ? 0 : max;
        int added = 0;
        for (Long courseId : desired) {
            if (!existing.add(courseId)) {
                continue;
            }
            clearDeletedLink(id, courseId);
            RouteCourse row = new RouteCourse();
            row.setRouteId(id);
            row.setCourseId(courseId);
            row.setSortOrder(++order);
            routeCourseMapper.insert(row);
            added++;
        }
        if (added > 0) {
            operationLogWriter.write("修改", "学习路线", route.getName());
        }
        return new AddCoursesResult(added);
    }

    @Transactional
    public void removeCourse(Long routeId, Long courseId) {
        LearningRoute route = requireRoute(routeId);
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM route_course WHERE route_id = ? AND course_id = ? AND delete_flag = 0",
                Long.class, routeId, courseId);
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "路线中没有该课程");
        }
        purgeRouteCourses(ids);
        operationLogWriter.write("修改", "学习路线", route.getName());
    }

    @Transactional
    public void sort(Long routeId, List<Long> courseIds) {
        LearningRoute route = requireRoute(routeId);
        List<Long> ordered = orderedIds(courseIds, true);
        List<Long> current = jdbcTemplate.queryForList(
                "SELECT course_id FROM route_course WHERE route_id = ? AND delete_flag = 0",
                Long.class, routeId);
        if (ordered.size() != current.size() || !new HashSet<>(ordered).equals(new HashSet<>(current))) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "排序课程与路线课程不一致");
        }
        int order = 1;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (Long courseId : ordered) {
            jdbcTemplate.update("""
                    UPDATE route_course SET sort_order = ?, updated_at = ?
                    WHERE route_id = ? AND course_id = ? AND delete_flag = 0
                    """, order++, now, routeId, courseId);
        }
        operationLogWriter.write("修改", "学习路线", route.getName());
    }

    private RouteDetail toDetail(LearningRoute route) {
        List<CourseLink> links = routeCourseMapper.listByRoute(route.getId());
        Map<Long, Subject> subjects = subjectsById();
        Set<Long> courseIds = new HashSet<>();
        for (CourseLink link : links) {
            courseIds.add(link.getCourseId());
        }
        Map<Long, Set<Long>> extras = extrasByCourse(courseIds);
        List<RouteCourseItem> courses = new ArrayList<>();
        List<RouteCourseItem> own = new ArrayList<>();
        List<RouteCourseItem> other = new ArrayList<>();
        int totalMinutes = 0;
        for (CourseLink link : links) {
            int minutes = RouteSupport.minutes(link.getDurationMinutes());
            totalMinutes += minutes;
            boolean cross = !belongs(link.getSubjectId(), extras.getOrDefault(link.getCourseId(), Set.of()), route.getSubjectId());
            String subjectName = link.getSubjectId() == null ? "其他学科" : nameOf(subjects.get(link.getSubjectId()));
            RouteCourseItem item = new RouteCourseItem(link.getRouteCourseId(), link.getCourseId(), link.getName(),
                    link.getBvid(), minutes, RouteSupport.durationText(minutes), link.getCoverUrl(),
                    RouteSupport.courseLink(link.getLink(), link.getBvid(), link.getSource()), link.getSource(),
                    link.getSubjectId(), subjectName, cross, link.getSortOrder() == null ? 0 : link.getSortOrder());
            courses.add(item);
            if (cross) {
                other.add(item);
            } else {
                own.add(item);
            }
        }
        Subject subject = subjects.get(route.getSubjectId());
        return new RouteDetail(route.getId(), route.getName(), route.getSubjectId(),
                subject == null ? null : subject.getName(), subject == null ? null : subject.getIcon(),
                route.getStatus(), route.getIcon(), route.getTargetEducation(), route.getDescription(),
                courses.size(), totalMinutes, RouteSupport.durationText(totalMinutes), courses, own, other);
    }

    private void syncCourses(Long routeId, List<Long> desired) {
        List<RouteCourse> existing = routeCourseMapper.selectList(Wrappers.<RouteCourse>lambdaQuery()
                .eq(RouteCourse::getRouteId, routeId));
        Map<Long, RouteCourse> byCourse = new HashMap<>();
        for (RouteCourse row : existing) {
            byCourse.putIfAbsent(row.getCourseId(), row);
        }
        Set<Long> keep = new HashSet<>(desired);
        List<Long> removeIds = existing.stream()
                .filter(row -> !keep.contains(row.getCourseId()))
                .map(RouteCourse::getId)
                .toList();
        purgeRouteCourses(removeIds);
        int order = 1;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (Long courseId : desired) {
            RouteCourse current = byCourse.get(courseId);
            if (current == null || removeIds.contains(current.getId())) {
                clearDeletedLink(routeId, courseId);
                RouteCourse row = new RouteCourse();
                row.setRouteId(routeId);
                row.setCourseId(courseId);
                row.setSortOrder(order);
                routeCourseMapper.insert(row);
            } else if (current.getSortOrder() == null || current.getSortOrder() != order) {
                jdbcTemplate.update("UPDATE route_course SET sort_order = ?, updated_at = ? WHERE id = ?",
                        order, now, current.getId());
            }
            order++;
        }
    }

    /**
     * route_course 的唯一键不含 delete_flag。清掉历史上的逻辑删除行，避免同一门课无法再次加入。
     */
    private void clearDeletedLink(Long routeId, Long courseId) {
        jdbcTemplate.update("""
                DELETE c FROM route_course_completion c
                INNER JOIN route_course rc ON rc.id = c.route_course_id
                WHERE rc.route_id = ? AND rc.course_id = ? AND rc.delete_flag = 1
                """, routeId, courseId);
        jdbcTemplate.update(
                "DELETE FROM route_course WHERE route_id = ? AND course_id = ? AND delete_flag = 1",
                routeId, courseId);
    }

    /**
     * route_course 的唯一键不含 delete_flag，逻辑删除后无法再次加入同一门课，因此关联行物理删除。
     */
    private void purgeRouteCourses(List<Long> routeCourseIds) {
        if (routeCourseIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", routeCourseIds.stream().map(id -> "?").toList());
        Object[] args = routeCourseIds.toArray();
        jdbcTemplate.update("DELETE FROM route_course_completion WHERE route_course_id IN (" + placeholders + ")", args);
        jdbcTemplate.update("DELETE FROM route_course WHERE id IN (" + placeholders + ")", args);
    }

    private Map<Long, List<CourseLink>> group(List<CourseLink> links) {
        Map<Long, List<CourseLink>> grouped = new HashMap<>();
        for (CourseLink link : links) {
            grouped.computeIfAbsent(link.getRouteId(), key -> new ArrayList<>()).add(link);
        }
        return grouped;
    }

    private Map<Long, Subject> subjectsById() {
        Map<Long, Subject> subjects = new HashMap<>();
        for (Subject subject : subjectMapper.selectList(Wrappers.<Subject>lambdaQuery().orderByAsc(Subject::getSortOrder).orderByAsc(Subject::getId))) {
            subjects.put(subject.getId(), subject);
        }
        return subjects;
    }

    private Map<Long, Set<Long>> extrasByCourse(Set<Long> courseIds) {
        if (courseIds != null && courseIds.isEmpty()) {
            return Map.of();
        }
        List<CourseSubject> rows = courseIds == null
                ? courseSubjectMapper.selectList(null)
                : courseSubjectMapper.selectList(Wrappers.<CourseSubject>lambdaQuery().in(CourseSubject::getCourseId, courseIds));
        Map<Long, Set<Long>> extras = new HashMap<>();
        for (CourseSubject row : rows) {
            extras.computeIfAbsent(row.getCourseId(), key -> new HashSet<>()).add(row.getSubjectId());
        }
        return extras;
    }

    private boolean belongs(Long primarySubjectId, Set<Long> extras, Long routeSubjectId) {
        return routeSubjectId.equals(primarySubjectId) || extras.contains(routeSubjectId);
    }

    private void assertCoursesExist(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return;
        }
        List<Course> found = courseMapper.selectBatchIds(courseIds);
        if (found.size() != courseIds.size()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "课程不存在或已删除");
        }
    }

    private List<Long> orderedIds(List<Long> courseIds, boolean rejectDuplicate) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long courseId : courseIds) {
            if (courseId == null || courseId <= 0) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "课程不能为空");
            }
            if (!ids.add(courseId) && rejectDuplicate) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "课程重复");
            }
        }
        return new ArrayList<>(ids);
    }

    private LearningRoute requireRoute(Long id) {
        LearningRoute route = learningRouteMapper.selectById(id);
        if (route == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "学习路线不存在");
        }
        return route;
    }

    private Subject requireSubject(Long id) {
        Subject subject = id == null ? null : subjectMapper.selectById(id);
        if (subject == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "关联学科不存在");
        }
        return subject;
    }

    private String nameOf(Subject subject) {
        return subject == null ? null : subject.getName();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
