package com.ls.agent.core.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.trace.entity.TraceSpanEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TraceSpanMapper extends BaseMapper<TraceSpanEntity> {
}
