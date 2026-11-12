package com.yunshu.zhixue.modules.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshu.zhixue.modules.subject.entity.Stage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StageMapper extends BaseMapper<Stage> {

    @Select("SELECT COUNT(*) FROM question WHERE delete_flag = 0 AND stage_id = #{stageId}")
    long countQuestions(@Param("stageId") Long stageId);

    @Select("SELECT COUNT(*) FROM question WHERE delete_flag = 0 AND status = 1 AND stage_id = #{stageId}")
    long countPublishedQuestions(@Param("stageId") Long stageId);
}
