package com.ls.agent.core.memory;

import com.ls.agent.core.memory.mapper.MemoryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMapperTest {

    @Test
    void searchActiveMemoriesUsesPostgresTsvectorRankAndTextFallback() throws NoSuchMethodException {
        Method method = MemoryMapper.class.getMethod(
                "searchActiveMemories",
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
                "ts_rank_cd(m.search_vector",
                "m.search_vector @@ query.ts_query",
                "m.content ilike",
                "regexp_replace(lower(m.content)",
                "[[:space:]]+",
                "(m.expires_at is null or m.expires_at > now())",
                "order by keyword_score desc"
        );
    }
}
