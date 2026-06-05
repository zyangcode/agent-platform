package com.ls.agent.core.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ls.agent.core.rag.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {

    @Select("""
            <script>
            select *
            from knowledge_documents
            where tenant_id = #{tenantId}
              and application_id = #{applicationId}
              and owner_user_id = #{ownerUserId}
              and (
                    profile_id = #{profileId}
                    or (profile_id is null and #{profileId} is null)
                  )
              and doc_hash = #{docHash}
              and status = 'INDEXED'
            limit 1
            </script>
            """)
    KnowledgeDocumentEntity selectActiveByScopeAndHash(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("profileId") Long profileId,
            @Param("docHash") String docHash
    );

    @Select("""
            <script>
            select *
            from knowledge_documents
            where id = #{documentId}
              and tenant_id = #{tenantId}
              and application_id = #{applicationId}
              and owner_user_id = #{ownerUserId}
              and (
                    profile_id = #{profileId}
                    or (profile_id is null and #{profileId} is null)
                  )
              and status = 'INDEXED'
            limit 1
            </script>
            """)
    KnowledgeDocumentEntity selectActiveByIdAndScope(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("profileId") Long profileId,
            @Param("documentId") Long documentId
    );

    @Update("""
            update knowledge_documents
            set status = 'DELETED',
                updated_at = current_timestamp
            where id = #{documentId}
            """)
    int disableById(@Param("documentId") Long documentId);
}
