package com.yunshu.zhixue.modules.interview.service;

import com.yunshu.zhixue.modules.interview.dto.InterviewSessionState.Item;
import com.yunshu.zhixue.modules.subject.entity.Subject;

/** 题库数量不足时补生成快照题。没有大模型时使用固定题干，保证同一方向和难度可复现。 */
public final class InterviewSnapshots {

    private static final String[] STEMS = {
            "请介绍「{name}」主要解决什么问题，并举一个你理解的应用场景。",
            "做「{name}」时，你会如何判断一个方案是否可行？",
            "「{name}」里如何保证输出质量稳定？",
            "请说明「{name}」中一个常见失败场景，以及你会怎么排查。",
            "如果让你向非技术同事解释「{name}」，你会怎么说？",
            "「{name}」上线前，你会做哪些验证？",
            "谈谈「{name}」里最容易被忽略的风险。",
            "如果要在两周内做出「{name}」的最小可用版本，你会怎么排期？"
    };

    private static final String[] REFERENCES = {
            "{name}用于把业务问题拆成可执行的流程。要点包括目标、输入、输出和约束。典型场景是用提示词、工具调用和知识库完成一项具体任务。",
            "先明确目标用户和成功标准，再评估数据、成本、时延和风险，最后用小范围试验验证效果。",
            "通过明确提示词、样例、校验规则和人工抽检来稳定输出。发现偏差后回看输入和评价标准再调整。",
            "常见失败是结果不符合预期。排查时先复现输入，再检查数据、提示词、工具返回和日志，最后给出修复和回归验证。",
            "用业务语言说明它能替人完成哪一步、需要什么输入、产出什么结果，并说明目前不能替代人工判断的边界。",
            "准备代表性用例，检查正确性、稳定性、时延和异常输入。记录失败样例，确认可以回滚后再上线。",
            "容易忽略数据来源不可靠、权限越界和结果无法追溯。需要限制输入范围、记录关键决策并保留人工确认。",
            "第一周明确场景和数据，完成主流程。第二周补异常处理、评价样例和演示。范围只保留一条核心路径。"
    };

    private InterviewSnapshots() {
    }

    public static Item create(Subject subject, int difficulty, int order, int serial) {
        String name = subject.getName() == null ? "该方向" : subject.getName();
        int index = Math.floorMod(serial, STEMS.length);
        Item item = new Item();
        item.setOrder(order);
        item.setQuestionId(null);
        item.setSubjectId(subject.getId());
        item.setDifficulty(difficulty);
        item.setContent(lead(difficulty) + STEMS[index].replace("{name}", name));
        item.setReferenceAnswer(REFERENCES[index].replace("{name}", name));
        item.setSource("ai");
        return item;
    }

    private static String lead(int difficulty) {
        if (difficulty <= 1) {
            return "请用基础概念回答：";
        }
        if (difficulty >= 3) {
            return "请说明取舍和边界：";
        }
        return "请结合一个具体场景回答：";
    }
}
