package com.yunshu.zhixue.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.zhixue.common.constant.AppConstants;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.config.AppProperties;
import com.yunshu.zhixue.modules.auth.dto.AuthPayloads.SmsCodeView;
import com.yunshu.zhixue.modules.student.entity.Student;
import com.yunshu.zhixue.modules.student.mapper.StudentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class SmsCodeService {

    public static final String REGISTER = "register";
    public static final String LOGIN = "login";
    public static final String RESET = "reset";

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate redis;
    private final StudentMapper studentMapper;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SmsCodeService(StringRedisTemplate redis, StudentMapper studentMapper, AppProperties properties) {
        this.redis = redis;
        this.studentMapper = studentMapper;
        this.properties = properties;
    }

    public SmsCodeView send(String mobile, String type) {
        boolean exists = studentExists(mobile);
        if (REGISTER.equals(type) && exists) {
            throw new BizException(ErrorCode.CONTACT_TAKEN, "手机号已注册");
        }
        if (RESET.equals(type) && !exists) {
            throw new BizException(ErrorCode.NOT_FOUND, "手机号未注册");
        }
        String dayKey = "sms:daycount:" + mobile;
        String current = redis.opsForValue().get(dayKey);
        if (current != null && Integer.parseInt(current) >= AppConstants.SMS_DAILY_LIMIT) {
            throw new BizException(ErrorCode.SMS_DAILY_LIMIT);
        }
        Boolean cooled = redis.opsForValue().setIfAbsent(
                "sms:cooldown:" + mobile, "1", Duration.ofSeconds(AppConstants.SMS_INTERVAL_SECONDS));
        if (!Boolean.TRUE.equals(cooled)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
        }
        Long count = redis.opsForValue().increment(dayKey);
        if (count != null && count == 1) {
            redis.expire(dayKey, untilNextShanghaiMidnight());
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(type, mobile), code, Duration.ofSeconds(AppConstants.SMS_CODE_TTL_SECONDS));
        redis.delete(failKey(type, mobile));
        if (properties.getSms().isExposeCode()) {
            log.info("SMS_CODE type={} mobile={} code={}", type, mobile, code);
        }
        String debugCode = properties.getSms().isExposeCode() ? code : null;
        return new SmsCodeView(AppConstants.SMS_CODE_TTL_SECONDS, debugCode);
    }

    public void verify(String type, String mobile, String code) {
        String key = codeKey(type, mobile);
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            registerFailure(type, mobile, key);
            throw new BizException(ErrorCode.SMS_INVALID);
        }
        redis.delete(key);
        redis.delete(failKey(type, mobile));
    }

    private void registerFailure(String type, String mobile, String codeKey) {
        String fail = failKey(type, mobile);
        Long count = redis.opsForValue().increment(fail);
        Long ttl = redis.getExpire(codeKey);
        if (ttl != null && ttl > 0) {
            redis.expire(fail, Duration.ofSeconds(ttl));
        } else {
            redis.expire(fail, Duration.ofSeconds(AppConstants.SMS_CODE_TTL_SECONDS));
        }
        if (count != null && count >= AppConstants.SMS_VERIFY_FAIL_LIMIT) {
            redis.delete(codeKey);
            redis.delete(fail);
        }
    }

    private boolean studentExists(String mobile) {
        Long count = studentMapper.selectCount(Wrappers.<Student>lambdaQuery().eq(Student::getMobile, mobile));
        return count != null && count > 0;
    }

    private Duration untilNextShanghaiMidnight() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI);
        ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(SHANGHAI);
        Duration duration = Duration.between(now, next);
        return duration.isZero() || duration.isNegative() ? Duration.ofSeconds(1) : duration;
    }

    private String codeKey(String type, String mobile) {
        return "sms:code:" + type + ":" + mobile;
    }

    private String failKey(String type, String mobile) {
        return "sms:fail:" + type + ":" + mobile;
    }
}
