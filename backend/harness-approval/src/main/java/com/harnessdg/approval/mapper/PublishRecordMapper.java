/**
 * 功能：发布记录 Mapper 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.approval.entity.PublishRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PublishRecordMapper extends BaseMapper<PublishRecord> {
}
