package com.ls.agent.core.quota.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.quota.entity.TokenUsageLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenUsageLogMapper extends BaseMapper<TokenUsageLogEntity> {
}
