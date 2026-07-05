package com.aihub.version.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VersionMapper extends BaseMapper<VersionEntity> {
}
