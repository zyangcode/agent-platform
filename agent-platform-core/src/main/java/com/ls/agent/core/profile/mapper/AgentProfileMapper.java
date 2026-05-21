package com.ls.agent.core.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.profile.entity.AgentProfileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentProfileMapper extends BaseMapper<AgentProfileEntity> {
}
