package com.ls.agent.core.skill.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.skill.api.SkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DefaultSkillExecutor implements SkillExecutor {

    private final ObjectMapper objectMapper;

    public DefaultSkillExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SkillExecuteResult execute(SkillExecuteCommand command) {
        String code = command.skillCode();
        if ("calculator".equals(code)) {
            return calculate(command);
        }
        if ("weather".equals(code)) {
            return mockWeather(command);
        }
        if ("search".equals(code)) {
            return mockSearch(command);
        }
        throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Unsupported skill: " + code);
    }

    private SkillExecuteResult calculate(SkillExecuteCommand command) {
        String expression = requiredText(command, "expression");
        BigDecimal value = new ExpressionParser(expression).parse();
        ObjectNode output = objectMapper.createObjectNode();
        output.put("result", format(value));
        return new SkillExecuteResult(true, command.skillCode(), output, null);
    }

    private SkillExecuteResult mockWeather(SkillExecuteCommand command) {
        String city = requiredText(command, "city");
        ObjectNode output = objectMapper.createObjectNode();
        output.put("summary", city + " mock weather: sunny, 26C");
        return new SkillExecuteResult(true, command.skillCode(), output, null);
    }

    private SkillExecuteResult mockSearch(SkillExecuteCommand command) {
        String query = requiredText(command, "query");
        ObjectNode item = objectMapper.createObjectNode();
        item.put("title", "Mock search result for " + query);
        item.put("url", "https://example.com/search");
        ObjectNode output = objectMapper.createObjectNode();
        output.set("results", objectMapper.createArrayNode().add(item));
        return new SkillExecuteResult(true, command.skillCode(), output, null);
    }

    private String requiredText(SkillExecuteCommand command, String field) {
        if (command.arguments() == null || command.arguments().get(field) == null
                || command.arguments().get(field).asText().isBlank()) {
            throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Missing skill argument: " + field);
        }
        return command.arguments().get(field).asText();
    }

    private String format(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.toPlainString();
        }
        return normalized.setScale(Math.min(normalized.scale(), 10), RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static final class ExpressionParser {

        private final String input;
        private int position;

        private ExpressionParser(String input) {
            this.input = input;
        }

        private BigDecimal parse() {
            BigDecimal result = parseExpression();
            skipWhitespace();
            if (position != input.length()) {
                throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Invalid calculator expression");
            }
            return result;
        }

        private BigDecimal parseExpression() {
            BigDecimal result = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    result = result.add(parseTerm());
                } else if (match('-')) {
                    result = result.subtract(parseTerm());
                } else {
                    return result;
                }
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal result = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    result = result.multiply(parseFactor());
                } else if (match('/')) {
                    result = result.divide(parseFactor(), 10, RoundingMode.HALF_UP);
                } else {
                    return result;
                }
            }
        }

        private BigDecimal parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return parseFactor().negate();
            }
            if (match('(')) {
                BigDecimal result = parseExpression();
                if (!match(')')) {
                    throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Invalid calculator expression");
                }
                return result;
            }
            return parseNumber();
        }

        private BigDecimal parseNumber() {
            skipWhitespace();
            int start = position;
            while (position < input.length()) {
                char current = input.charAt(position);
                if ((current >= '0' && current <= '9') || current == '.') {
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new BizException(ErrorCode.SKILL_EXECUTE_FAILED, "Invalid calculator expression");
            }
            return new BigDecimal(input.substring(start, position));
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }
    }
}
