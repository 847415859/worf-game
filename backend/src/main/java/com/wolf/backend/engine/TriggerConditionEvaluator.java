package com.wolf.backend.engine;

import java.util.Arrays;
import java.util.List;

/**
 * 极简条件求值器，解析 role_definition.skill_config 中 trigger_action.condition 的两种写法
 * （见文档7.6节示例）："death_cause != poison" 和 "death_cause in [vote_out, poison]"。
 * 只服务于 on_death 触发型技能，不是通用表达式引擎。
 */
public final class TriggerConditionEvaluator {

    private TriggerConditionEvaluator() {
    }

    public static boolean evaluate(String condition, String deathCause) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        String c = condition.trim();
        if (c.contains("!=")) {
            String expected = c.substring(c.indexOf("!=") + 2).trim();
            return !expected.equalsIgnoreCase(deathCause);
        }
        if (c.contains("==")) {
            String expected = c.substring(c.indexOf("==") + 2).trim();
            return expected.equalsIgnoreCase(deathCause);
        }
        if (c.contains(" in ")) {
            int bracketStart = c.indexOf('[');
            int bracketEnd = c.indexOf(']');
            if (bracketStart >= 0 && bracketEnd > bracketStart) {
                String inner = c.substring(bracketStart + 1, bracketEnd);
                List<String> values = Arrays.stream(inner.split(",")).map(String::trim).toList();
                return values.stream().anyMatch(v -> v.equalsIgnoreCase(deathCause));
            }
        }
        return false;
    }
}
