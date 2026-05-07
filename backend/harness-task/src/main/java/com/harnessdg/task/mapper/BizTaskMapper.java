package com.harnessdg.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.task.entity.BizTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BizTaskMapper extends BaseMapper<BizTask> {
}
