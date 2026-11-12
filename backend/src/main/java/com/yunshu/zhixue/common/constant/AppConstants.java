package com.yunshu.zhixue.common.constant;

public final class AppConstants {

    public static final int INTERVIEW_DAILY_QUOTA = 5;
    public static final int DEFAULT_INTERVIEW_QUESTION_COUNT = 10;
    public static final int INTERVIEW_ANSWER_TIMEOUT_MINUTES = 5;
    public static final int SHORT_ANSWER_PASS_SCORE = 6;
    public static final int WRONG_MASTERED_THRESHOLD = 3;
    public static final int LOGIN_FAIL_LOCK_THRESHOLD = 5;
    public static final int LOGIN_LOCK_MINUTES = 5;
    public static final int SMS_INTERVAL_SECONDS = 60;
    public static final int SMS_DAILY_LIMIT = 10;
    public static final int SMS_CODE_TTL_SECONDS = 300;
    public static final int SMS_VERIFY_FAIL_LIMIT = 5;
    public static final int RESET_TOKEN_TTL_MINUTES = 10;
    public static final int AUTH_RATE_PER_MINUTE = 20;
    public static final String MOBILE_PATTERN = "^1[3-9]\\d{9}$";
    public static final String PASSWORD_PATTERN = "^\\S{6,20}$";
    public static final String PASSWORD_MESSAGE = "密码需为 6~20 位且不能包含空格";

    private AppConstants() {
    }
}
