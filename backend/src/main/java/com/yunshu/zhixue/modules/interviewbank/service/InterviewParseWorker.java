package com.yunshu.zhixue.modules.interviewbank.service;

import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.Sample;
import com.yunshu.zhixue.modules.interviewbank.dto.InterviewPayloads.UploadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterviewParseWorker {

    private static final Logger log = LoggerFactory.getLogger(InterviewParseWorker.class);

    private final InterviewMarkdownParser parser;
    private final InterviewAdminService interviewAdminService;

    public InterviewParseWorker(InterviewMarkdownParser parser, @Lazy InterviewAdminService interviewAdminService) {
        this.parser = parser;
        this.interviewAdminService = interviewAdminService;
    }

    @Async("interviewParseExecutor")
    public void run(String taskId, long subjectId, String fileName, String text) {
        try {
            List<Sample> items = parser.parse(text);
            interviewAdminService.saveUploaded(subjectId, fileName, items);
            int end = Math.min(3, items.size());
            interviewAdminService.writeTask(taskId, new UploadStatus("done", items.size(), items.subList(0, end), null));
        } catch (BizException ex) {
            interviewAdminService.writeTask(taskId, new UploadStatus("failed", 0, List.of(), ex.getMessage()));
        } catch (RuntimeException ex) {
            log.warn("面试题文档解析失败 task={}", taskId, ex);
            interviewAdminService.writeTask(taskId, new UploadStatus("failed", 0, List.of(), "解析失败"));
        }
    }
}
