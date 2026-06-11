package com.odin.catalog.shared.models.policy;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolicyEvaluationResultPayload(
    String datasetId,
    String tenantId,
    String action,
    boolean granted,
    List<DecisionTuple> decisions
) {
    public record DecisionTuple(String action, String result, boolean delegated) {}
}
