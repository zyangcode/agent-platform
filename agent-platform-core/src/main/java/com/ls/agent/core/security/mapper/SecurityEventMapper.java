package com.ls.agent.core.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.security.entity.SecurityEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecurityEventMapper extends BaseMapper<SecurityEventEntity> {
}
