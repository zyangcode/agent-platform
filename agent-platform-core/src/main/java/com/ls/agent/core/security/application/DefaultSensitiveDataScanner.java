package com.ls.agent.core.security.application;

import com.ls.agent.core.security.api.SensitiveDataScanner;
import com.ls.agent.core.security.dto.SensitiveDataFindingDTO;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class DefaultSensitiveDataScanner implements SensitiveDataScanner {

    private static final List<RegexRule> RULES = List.of(
            new RegexRule("PHONE", Pattern.compile("\\b1[3-9]\\d{9}\\b"), DefaultSensitiveDataScanner::maskPhone),
            new RegexRule("EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"), DefaultSensitiveDataScanner::maskEmail),
            new RegexRule("ID_CARD", Pattern.compile("\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b"), DefaultSensitiveDataScanner::maskIdCard),
            new RegexRule("API_KEY_PATTERN", Pattern.compile("\\bsk-[A-Za-z0-9]{32,}\\b"), DefaultSensitiveDataScanner::maskApiKey)
    );

    @Override
    public List<SensitiveDataFindingDTO> scan(String text, String location) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return RULES.stream()
                .flatMap(rule -> rule.pattern().matcher(text).results()
                        .map(match -> match.group())
                        .map(source -> new SensitiveDataFindingDTO(
                                rule.eventType(),
                                location,
                                sha256(source),
                                rule.masker().apply(source)
                        )))
                .toList();
    }

    private static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String maskPhone(String source) {
        return source.substring(0, 3) + "****" + source.substring(source.length() - 4);
    }

    private static String maskEmail(String source) {
        int at = source.indexOf('@');
        if (at <= 1) {
            return "***" + source.substring(at);
        }
        return source.charAt(0) + "***" + source.substring(at);
    }

    private static String maskIdCard(String source) {
        return source.substring(0, 6) + "********" + source.substring(source.length() - 4);
    }

    private static String maskApiKey(String source) {
        return "sk-****" + source.substring(source.length() - 4);
    }

    private record RegexRule(
            String eventType,
            Pattern pattern,
            Function<String, String> masker
    ) {
    }
}
