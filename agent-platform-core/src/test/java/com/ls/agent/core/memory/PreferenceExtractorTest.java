package com.ls.agent.core.memory;

import com.ls.agent.core.memory.application.PreferenceExtractor;
import com.ls.agent.core.memory.command.RecordMemoryCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreferenceExtractorTest {

    private final PreferenceExtractor extractor = new PreferenceExtractor();

    @Test
    void extractsPositivePreferenceFromUserMessage() {
        List<RecordMemoryCommand> result = extractor.extract(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "我喜欢傍晚打篮球，回答尽量简洁"
        );

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RecordMemoryCommand::memoryCategory)
                .containsOnly("preference");
        assertThat(result).extracting(RecordMemoryCommand::content)
                .containsExactly(
                        "用户偏好：傍晚打篮球",
                        "用户偏好：回答尽量简洁"
                );
        assertThat(result).extracting(RecordMemoryCommand::tags)
                .containsExactly(List.of("preference", "positive"), List.of("preference", "positive"));
        assertThat(result).extracting(RecordMemoryCommand::importance)
                .containsOnly(0.8);
    }

    @Test
    void extractsNegativePreferenceFromUserMessage() {
        List<RecordMemoryCommand> result = extractor.extract(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "我不喜欢太长的解释"
        );

        assertThat(result).singleElement()
                .satisfies(command -> {
                    assertThat(command.memoryType()).isEqualTo("PREFERENCE");
                    assertThat(command.memoryCategory()).isEqualTo("preference");
                    assertThat(command.content()).isEqualTo("用户不偏好：太长的解释");
                    assertThat(command.tags()).containsExactly("preference", "negative");
                    assertThat(command.slotHint()).isEqualTo("preference");
                });
    }

    @Test
    void extractsEnglishPositivePreferenceFromUserMessage() {
        List<RecordMemoryCommand> result = extractor.extract(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "I prefer turquoise scoreboards for basketball planning."
        );

        assertThat(result).singleElement()
                .satisfies(command -> {
                    assertThat(command.memoryType()).isEqualTo("PREFERENCE");
                    assertThat(command.memoryCategory()).isEqualTo("preference");
                    assertThat(command.content()).isEqualTo("User preference: turquoise scoreboards for basketball planning");
                    assertThat(command.tags()).containsExactly("preference", "positive");
                    assertThat(command.slotHint()).isEqualTo("preference");
                });
    }

    @Test
    void ignoresMessagesWithoutPreferenceSignal() {
        List<RecordMemoryCommand> result = extractor.extract(
                1L,
                10001L,
                20001L,
                50001L,
                90001L,
                "今天重庆天气怎么样"
        );

        assertThat(result).isEmpty();
    }
}
