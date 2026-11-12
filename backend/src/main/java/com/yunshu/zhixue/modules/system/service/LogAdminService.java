package com.yunshu.zhixue.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.exception.ErrorCode;
import com.yunshu.zhixue.common.response.PageResult;
import com.yunshu.zhixue.modules.system.dto.SystemPayloads.LogItem;
import com.yunshu.zhixue.modules.system.entity.OperationLog;
import com.yunshu.zhixue.modules.system.mapper.OperationLogMapper;
import com.yunshu.zhixue.support.OperationLogWriter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LogAdminService {

    private static final int EXPORT_LIMIT = 10_000;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationLogMapper operationLogMapper;
    private final OperationLogWriter operationLogWriter;

    public LogAdminService(OperationLogMapper operationLogMapper, OperationLogWriter operationLogWriter) {
        this.operationLogMapper = operationLogMapper;
        this.operationLogWriter = operationLogWriter;
    }

    public PageResult<LogItem> page(String keyword, String operationType, LocalDate startDate, LocalDate endDate, long page, long pageSize) {
        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, pageSize), query(keyword, operationType, startDate, endDate));
        List<LogItem> list = result.getRecords().stream().map(this::toItem).toList();
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    public LogItem detail(Long id) {
        OperationLog log = operationLogMapper.selectById(id);
        if (log == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "日志不存在");
        }
        return toItem(log);
    }

    public ResponseEntity<byte[]> export(String keyword, String operationType, LocalDate startDate, LocalDate endDate) {
        long total = operationLogMapper.selectCount(query(keyword, operationType, startDate, endDate));
        if (total > EXPORT_LIMIT) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "导出超过 1 万行，请缩小筛选范围");
        }
        List<OperationLog> rows = operationLogMapper.selectList(query(keyword, operationType, startDate, endDate));
        StringBuilder csv = new StringBuilder("\uFEFF用户,操作,目标,IP地址,时间\n");
        for (OperationLog row : rows) {
            csv.append(cell(row.getUsername())).append(',')
                    .append(cell(row.getAction())).append(',')
                    .append(cell(row.getTargetType() + " / " + row.getTargetName())).append(',')
                    .append(cell(row.getIp())).append(',')
                    .append(cell(format(row.getCreatedAt())))
                    .append('\n');
        }
        operationLogWriter.write("导出", "操作日志", "导出 " + rows.size() + " 条");
        ContentDisposition disposition = ContentDisposition.attachment().filename("operation-logs.csv", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void clear() {
        operationLogMapper.delete(Wrappers.lambdaQuery());
        operationLogWriter.write("删除", "操作日志", "清空全部日志");
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationLog> query(
            String keyword, String operationType, LocalDate startDate, LocalDate endDate) {
        var query = Wrappers.<OperationLog>lambdaQuery().orderByDesc(OperationLog::getId);
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper.like(OperationLog::getUsername, text)
                    .or().like(OperationLog::getAction, text)
                    .or().like(OperationLog::getTargetType, text)
                    .or().like(OperationLog::getTargetName, text));
        }
        if (StringUtils.hasText(operationType)) {
            query.eq(OperationLog::getAction, operationType.trim());
        }
        if (startDate != null) {
            query.ge(OperationLog::getCreatedAt, toUtc(startDate));
        }
        if (endDate != null) {
            query.lt(OperationLog::getCreatedAt, toUtc(endDate.plusDays(1)));
        }
        return query;
    }

    private LocalDateTime toUtc(LocalDate date) {
        return date.atStartOfDay(SHANGHAI).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private LogItem toItem(OperationLog log) {
        return new LogItem(log.getId(), log.getUsername(), log.getAction(), log.getTargetType(), log.getTargetName(), log.getIp(), log.getCreatedAt());
    }

    private String format(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.atOffset(ZoneOffset.UTC).atZoneSameInstant(SHANGHAI).format(FORMATTER);
    }

    private String cell(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
