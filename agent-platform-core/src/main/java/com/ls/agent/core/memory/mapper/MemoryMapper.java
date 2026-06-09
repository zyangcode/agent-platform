package com.ls.agent.core.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.memory.entity.MemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryMapper extends BaseMapper<MemoryEntity> {

    @Select("""
            <script>
            with query as (
                select websearch_to_tsquery('simple', #{queryText}) as ts_query
            )
            select m.*,
                   (
                       ts_rank_cd(m.search_vector, query.ts_query)
                       + case
                           <if test="terms != null and terms.size() > 0">
                           when (
                               <foreach collection="terms" item="term" separator=" or ">
                                   (m.content ilike concat('%', #{term}, '%')
                                    or regexp_replace(lower(m.content), '[[:space:]]+', '', 'g') ilike concat('%', lower(#{term}), '%'))
                               </foreach>
                           ) then 0.05
                           </if>
                           else 0.0
                         end
                   )::float8 as keyword_score
            from memories m, query
            where m.tenant_id = #{tenantId}
              and m.user_id = #{userId}
              and m.status = 'ACTIVE'
              and (m.application_id is null or m.application_id = #{applicationId})
              and (m.profile_id is null or m.profile_id = #{profileId})
              and (m.expires_at is null or m.expires_at > now())
              and (
                  m.search_vector @@ query.ts_query
                  <if test="terms != null and terms.size() > 0">
                  or (
                      <foreach collection="terms" item="term" separator=" or ">
                          (m.content ilike concat('%', #{term}, '%')
                           or regexp_replace(lower(m.content), '[[:space:]]+', '', 'g') ilike concat('%', lower(#{term}), '%'))
                      </foreach>
                  )
                  </if>
              )
            order by keyword_score desc, m.importance desc, m.updated_at desc
            limit #{limit}
            </script>
            """)
    List<MemoryEntity> searchActiveMemories(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("userId") Long userId,
            @Param("profileId") Long profileId,
            @Param("terms") List<String> terms,
            @Param("queryText") String queryText,
            @Param("limit") int limit
    );
}
