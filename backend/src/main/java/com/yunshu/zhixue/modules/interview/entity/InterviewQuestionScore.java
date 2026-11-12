package com.yunshu.zhixue.modules.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("interview_question_score")
public class InterviewQuestionScore {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long interviewRecordId;
    private Integer questionOrder;
    private Long questionId;
    private String questionText;
    private String studentAnswer;
    private BigDecimal score;
    private String aiFeedback;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleteFlag;
}
