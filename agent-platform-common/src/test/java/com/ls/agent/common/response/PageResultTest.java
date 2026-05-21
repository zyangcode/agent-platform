package com.ls.agent.common.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void ofCalculatesTotalPages() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 2, 10, 21);

        assertThat(result.records()).containsExactly("a", "b");
        assertThat(result.pageNo()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void emptyUsesRequestedPageAndSize() {
        PageResult<String> result = PageResult.empty(1, 20);

        assertThat(result.records()).isEmpty();
        assertThat(result.pageNo()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
