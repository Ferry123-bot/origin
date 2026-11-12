package com.yunshu.zhixue.modules.student.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.AvatarView;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.PasswordChangeRequest;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.ProfileStats;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.ProfileUpdateRequest;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.ProfileView;
import com.yunshu.zhixue.modules.student.dto.ProfilePayloads.StudyStats;
import com.yunshu.zhixue.modules.student.dto.ProfileRows.PracticeTotal;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.ProfileMapper;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import com.yunshu.zhixue.security.JwtService;
import com.yunshu.zhixue.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StudentPortalService {

    private final StudentMapper studentMapper;
    private final ProfileMapper profileMapper;
    private final AvatarStorageService avatarStorageService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public StudentPortalService(StudentMapper studentMapper, ProfileMapper profileMapper,
                                AvatarStorageService avatarStorageService, PasswordEncoder passwordEncoder,
                                TokenService tokenService) {
        this.studentMapper = studentMapper;
        this.profileMapper = profileMapper;
        this.avatarStorageService = avatarStorageService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public ProfileView profile() {
        Student student = requireStudent();
        long id = student.getId();
        PracticeTotal total = profileMapper.practiceTotal(id);
        long practiced = total == null || total.getTotalCount() == null ? 0 : total.getTotalCount();
        long correct = total == null || total.getCorrectCount() == null ? 0 : total.getCorrectCount();
        int accuracy = practiced == 0 ? 0 : (int) Math.round(correct * 100.0 / practiced);
        return new ProfileView(
                id,
                student.getNickname(),
                maskMobile(student.getMobile()),
                student.getEmail(),
                student.getGender() == null ? 0 : student.getGender(),
                student.getPreferredSubjectId(),
                student.getAvatar(),
                student.getGuideFinished() != null && student.getGuideFinished() == 1,
                new ProfileStats(
                        saturate(practiced),
                        saturate(profileMapper.interviewCount(id)),
                        saturate(profileMapper.wrongCount(id))),
                new StudyStats(saturate(profileMapper.practiceDays(id)), accuracy));
    }

    @Transactional
    public void update(ProfileUpdateRequest request) {
        Student student = requireStudent();
        String nickname = request.nickname().trim();
        if (nickname.isEmpty() || nickname.length() > 20) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "用户名需为 1~20 个字符");
        }
        String email = request.email() == null ? "" : request.email().trim();
        String avatar = normalizeAvatar(request.avatar());
        if (!email.isEmpty()) {
            Student owner = studentMapper.selectOne(Wrappers.<Student>lambdaQuery()
                    .eq(Student::getEmail, email)
                    .ne(Student::getId, student.getId()));
            if (owner != null) {
                throw new BizException(ErrorCode.CONTACT_TAKEN, "邮箱已被使用");
            }
        }
        try {
            studentMapper.update(null, Wrappers.<Student>lambdaUpdate()
                    .eq(Student::getId, student.getId())
                    .set(Student::getNickname, nickname)
                    .set(Student::getEmail, email.isEmpty() ? null : email)
                    .set(Student::getGender, request.gender())
                    .set(Student::getAvatar, avatar));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.CONTACT_TAKEN, "邮箱已被使用");
        }
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request, HttpServletRequest httpRequest) {
        Student student = requireStudent();
        if (!passwordEncoder.matches(request.oldPassword(), student.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "原密码错误");
        }
        studentMapper.update(null, Wrappers.<Student>lambdaUpdate()
                .eq(Student::getId, student.getId())
                .set(Student::getPasswordHash, passwordEncoder.encode(request.newPassword())));
        tokenService.revokeAll(JwtService.AUDIENCE_PORTAL, student.getId(),
                httpRequest.getHeader("Authorization"), httpRequest.getHeader("X-Refresh-Token"));
    }

    public void finishGuide() {
        Student student = requireStudent();
        studentMapper.update(null, Wrappers.<Student>lambdaUpdate()
                .eq(Student::getId, student.getId())
                .set(Student::getGuideFinished, 1));
    }

    public AvatarView uploadAvatar(MultipartFile file) {
        requireStudent();
        return new AvatarView(avatarStorageService.upload(file));
    }

    private String normalizeAvatar(String avatar) {
        if (avatar == null || avatar.isBlank()) {
            return null;
        }
        String value = avatar.trim();
        if (!value.startsWith("/files/")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "头像地址不正确");
        }
        String object = value.substring("/files/".length());
        if (!AvatarStorageService.OBJECT_NAME.matcher(object).matches()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "头像地址不正确");
        }
        return value;
    }

    private Student requireStudent() {
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
        return student;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private int saturate(long value) {
        return (int) Math.min(Math.max(value, 0), Integer.MAX_VALUE);
    }
}
