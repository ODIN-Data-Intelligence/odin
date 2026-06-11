package com.odin.catalog.policy.api.v1.dto;

import java.util.List;

public record EvaluationResponse(
    boolean granted,
    String policyLevel,
    List<DecisionTuple> decisions
) {
    public record DecisionTuple(String action, String result, boolean delegated) {}
}
