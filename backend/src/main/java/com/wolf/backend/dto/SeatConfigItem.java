package com.wolf.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 对应需求文档3.2节 seats 结构 */
@Data
public class SeatConfigItem {
    @JsonProperty("seat_no")
    private int seatNo;

    /** ai / human */
    @JsonProperty("occupant_type")
    private String occupantType;

    @JsonProperty("character_id")
    private Long characterId;

    @JsonProperty("user_id")
    private Long userId;

    /** occupant_type=ai 且 character_id 为空时按段位自动匹配，见3.2节 */
    @JsonProperty("auto_match_tier")
    private String autoMatchTier;
}
