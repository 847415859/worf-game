package com.wolf.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleBoardItem {
    @JsonProperty("role_key")
    private String roleKey;
    private int count;
}
