package com.wolf.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 对应需求文档3.2节 rule_config schema 与14.2节默认规则基准表。
 * 字段名与JSON schema保持一致（snake_case），便于直接落库/回显。
 */
@Data
public class RuleConfig {

    @JsonProperty("enable_sheriff")
    private boolean enableSheriff = true;

    @JsonProperty("sheriff_vote_weight")
    private double sheriffVoteWeight = 1.5;

    @JsonProperty("witch_dual_potion_same_night")
    private boolean witchDualPotionSameNight = false;

    @JsonProperty("witch_self_save_policy")
    private String witchSelfSavePolicy = "first_night_only_disabled";

    @JsonProperty("poison_death_has_last_words")
    private boolean poisonDeathHasLastWords = false;

    /**
     * 夜间死亡（刀杀/毒杀）遗言策略："first_night_only"仅首夜夜间死亡有遗言、之后每晚都没有（竞技规则，
     * 见12人黄金标准局法官台词参考）；"always"为旧版默认——刀杀总有遗言，毒杀单独看poison_death_has_last_words。
     * 放逐出局(vote_out)与猎人开枪(hunter_shot)不受此项影响，规则见3.8/3.9.2节，始终按各自逻辑判断。
     */
    @JsonProperty("night_death_last_words_policy")
    private String nightDeathLastWordsPolicy = "first_night_only";

    @JsonProperty("tie_vote_rule")
    private String tieVoteRule = "pk_speech_then_non_pk_revote";

    /** 存在真实人类玩家时的默认值，见文档4.2.1节：按座位是否有human判定，而非mode字段 */
    @JsonProperty("irrationality_factor")
    private double irrationalityFactor = 0.15;

    @JsonProperty("same_night_guard_and_heal_rule")
    private String sameNightGuardAndHealRule = "target_dies";

    @JsonProperty("turn_timeout_seconds")
    private int turnTimeoutSeconds = 30;

    @JsonProperty("timeout_vote_policy")
    private String timeoutVotePolicy = "abstain";

    @JsonProperty("max_llm_retry")
    private int maxLlmRetry = 2;

    @JsonProperty("session_max_duration_minutes")
    private int sessionMaxDurationMinutes = 120;

    public static RuleConfig defaultsForSeats(boolean hasHumanSeat) {
        RuleConfig config = new RuleConfig();
        config.setIrrationalityFactor(hasHumanSeat ? 0.15 : 0.5);
        return config;
    }
}
