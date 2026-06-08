package com.ls.agent.core.rag;

import com.ls.agent.core.rag.mapper.KnowledgeChunkMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkMapperTest {

    @Test
    void searchActiveChunksUsesPostgresTsvectorRank() throws NoSuchMethodException {
        Method method = KnowledgeChunkMapper.class.getMethod(
                "searchActiveChunks",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                java.util.List.class,
                String.class,
                int.class
        );

        String sql = String.join("\n", method.getAnnotation(Select.class).value());

        assertThat(sql).contains(
                "websearch_to_tsquery('simple'",
                "ts_rank_cd(c.search_vector",
                "c.search_vector @@ query.ts_query",
                "order by keyword_score desc"
        );
        assertThat(sql).doesNotContain("c.content ilike");
    }
}
