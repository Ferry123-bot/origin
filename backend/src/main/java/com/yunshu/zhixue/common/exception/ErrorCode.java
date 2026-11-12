package com.yunshu.zhixue.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(10000, HttpStatus.BAD_REQUEST, "参数校验失败"),
    UNAUTHORIZED(10001, HttpStatus.UNAUTHORIZED, "未认证或凭证失效"),
    FORBIDDEN(10002, HttpStatus.FORBIDDEN, "无权限"),
    NOT_FOUND(10003, HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(10004, HttpStatus.CONFLICT, "资源冲突"),
    TOO_MANY_REQUESTS(10005, HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试"),
    INTERNAL_ERROR(10006, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),
    BAD_JSON(10007, HttpStatus.BAD_REQUEST, "请求体格式错误"),
    BAD_FILE(10008, HttpStatus.BAD_REQUEST, "文件类型或大小不符"),

    CONTACT_TAKEN(20001, HttpStatus.CONFLICT, "手机号或邮箱已被使用"),
    SMS_INVALID(20002, HttpStatus.BAD_REQUEST, "验证码错误或已失效"),
    BAD_CREDENTIALS(20003, HttpStatus.BAD_REQUEST, "账号或密码错误"),
    ACCOUNT_LOCKED(20004, HttpStatus.LOCKED, "账号已锁定，请稍后再试"),
    ACCOUNT_DISABLED(20005, HttpStatus.FORBIDDEN, "账号已禁用"),
    SMS_DAILY_LIMIT(20006, HttpStatus.TOO_MANY_REQUESTS, "今日获取次数已达上限"),
    INTERVIEW_QUOTA_EXHAUSTED(20007, HttpStatus.TOO_MANY_REQUESTS, "今日面试次数已用尽"),
    INTERVIEW_SESSION_EXPIRED(20008, HttpStatus.NOT_FOUND, "面试会话不存在或已过期"),
    INTERVIEW_QUESTION_SHORTAGE(20009, HttpStatus.CONFLICT, "可出题数量不足"),
    AI_UNAVAILABLE(20010, HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂不可用"),
    WRONG_QUESTION_NOT_FOUND(20011, HttpStatus.NOT_FOUND, "错题不存在"),

    NAME_EXISTS(30001, HttpStatus.CONFLICT, "名称或编码已存在"),
    MOBILE_EXISTS(30002, HttpStatus.CONFLICT, "手机号已存在"),
    ROLE_HAS_USERS(30003, HttpStatus.CONFLICT, "角色下有用户，不能删除"),
    BUILTIN_PROTECTED(30004, HttpStatus.CONFLICT, "内置账号或超级管理员角色不可操作"),
    IMPORT_INVALID(30005, HttpStatus.BAD_REQUEST, "导入校验失败"),
    ILLEGAL_STATUS(30006, HttpStatus.CONFLICT, "当前状态不允许该操作"),
    HAS_RELATIONS(30007, HttpStatus.CONFLICT, "存在关联内容，不能删除"),
    PERMISSION_SAVE_FAILED(30008, HttpStatus.BAD_REQUEST, "权限保存失败"),
    ADMIN_BAD_CREDENTIALS(30009, HttpStatus.BAD_REQUEST, "账号或密码错误"),
    ADMIN_LOCKED(30010, HttpStatus.LOCKED, "账号已锁定，请稍后再试"),
    ADMIN_DISABLED(30011, HttpStatus.FORBIDDEN, "账号已禁用");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
