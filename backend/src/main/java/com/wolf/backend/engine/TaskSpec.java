package com.wolf.backend.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSpec {
    private String actionType;
    private String instruction;
    private List<RuntimePlayer> candidateTargets;
    private boolean allowAbstain;
}
