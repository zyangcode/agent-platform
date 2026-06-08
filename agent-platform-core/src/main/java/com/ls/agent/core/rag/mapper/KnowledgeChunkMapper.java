package com.ls.agent.core.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.rag.entity.KnowledgeChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {

    @Update("""
            update knowledge_chunks
            set status = 'DELETED',
                updated_at = current_timestamp
            where tenant_id = #{tenantId}
              and application_id = #{applicationId}
              and document_id = #{documentId}
              and status = 'ACTIVE'
            """)
    int disableByDocumentId(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("documentId") Long documentId
    );

    @Select("""
            <script>
            with query as (
                select websearch_to_tsquery('simple', #{queryText}) as ts_query
            )
            select c.*,
                   d.title as title,
                   d.source_uri as source_uri,
                   ts_rank_cd(c.search_vector, query.ts_query)::float8 as keyword_score
            from knowledge_chunks c
            join knowledge_documents d on d.id = c.document_id
            cross join query
            where c.tenant_id = #{tenantId}
              and c.application_id = #{applicationId}
              and d.owner_user_id = #{ownerUserId}
              <if test="profileId != null">
                and (d.profile_id is null or d.profile_id = #{profileId})
              </if>
              <if test="profileId == null">
                and d.profile_id is null
              </if>
              and c.status = 'ACTIVE'
              and d.status = 'INDEXED'
              and c.search_vector @@ query.ts_query
            order by keyword_score desc, c.chunk_index asc
            limit #{limit}
            </script>
            """)
    List<KnowledgeChunkEntity> searchActiveChunks(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("profileId") Long profileId,
            @Param("terms") List<String> terms,
            @Param("queryText") String queryText,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            select c.*,
                   d.title as title,
                   d.source_uri as source_uri
            from knowledge_chunks c
            join knowledge_documents d on d.id = c.document_id
            where c.tenant_id = #{tenantId}
              and c.application_id = #{applicationId}
              and d.owner_user_id = #{ownerUserId}
              <if test="profileId != null">
                and (d.profile_id is null or d.profile_id = #{profileId})
              </if>
              <if test="profileId == null">
                and d.profile_id is null
              </if>
              and c.status = 'ACTIVE'
              and d.status = 'INDEXED'
              and c.id in
              <foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
                  #{chunkId}
              </foreach>
            </script>
            """)
    List<KnowledgeChunkEntity> selectActiveChunksByIds(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("profileId") Long profileId,
            @Param("chunkIds") List<Long> chunkIds
    );
}
