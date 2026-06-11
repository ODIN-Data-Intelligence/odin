package com.odin.catalog.policy.infrastructure.odre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Internal implementation of ODRE Algorithm 1 (Cimmino et al., 2025, Computers & Security).
 *
 * Supports:
 *   A-Level  — pure ODRL JSON-LD; static constraint values
 *   B1-Level — variable injection: [=varName] placeholders resolved from the M map
 *
 * Permissions that fire add (action, "true") to the UsageDecision.
 * Obligations that fire add (action, action) — delegated to the caller.
 * Prohibitions that fire remove any same-action permission from the decision.
 * An empty result set means all permission constraints failed (access denied).
 */
public class OdreEngine {

    private static final Logger log = LoggerFactory.getLogger(OdreEngine.class);

    private final ObjectMapper mapper;

    public OdreEngine(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // Algorithm 1: enforce(P*, M, F) → D
    public Set<UsageDecisionTuple> enforce(OdrePolicy policy, Map<String, Object> M, Map<String, Object> F) {
        // Step 1: reduce — inject M variables into B1/B2 placeholders
        String resolvedJson = injectVariables(policy.getJson(), M);

        JsonNode root;
        try {
            root = mapper.readTree(resolvedJson);
        } catch (Exception e) {
            log.error("action=POLICY_PARSE_FAILED error={}", e.getMessage());
            return Set.of();
        }

        // Steps 2-4: iterate rules, evaluate constraints, build decision set
        Set<UsageDecisionTuple> granted = new LinkedHashSet<>();
        Set<String> prohibited = new HashSet<>();

        // Permissions: if constraints pass → (action, "true")
        evaluateRuleArray(root.path("permission"), granted, false);

        // Obligations: if constraints pass → (action, action) delegation
        evaluateRuleArray(root.path("obligation"), granted, true);

        // Prohibitions: if constraints pass → veto same-action permission
        JsonNode prohibitions = root.path("prohibition");
        if (prohibitions.isArray()) {
            for (JsonNode rule : prohibitions) {
                if (constraintsPass(rule.path("constraint"))) {
                    String action = extractAction(rule);
                    if (action != null) prohibited.add(action);
                }
            }
        }

        granted.removeIf(t -> prohibited.contains(t.action()));
        log.debug("action=ODRE_ENFORCE_DONE decisions={} prohibited={}", granted.size(), prohibited.size());
        return Collections.unmodifiableSet(granted);
    }

    private void evaluateRuleArray(JsonNode rules, Set<UsageDecisionTuple> out, boolean delegate) {
        if (!rules.isArray()) return;
        for (JsonNode rule : rules) {
            if (constraintsPass(rule.path("constraint"))) {
                String action = extractAction(rule);
                if (action != null) {
                    out.add(new UsageDecisionTuple(action, delegate ? action : "true"));
                }
            }
        }
    }

    // reduce(P*, M, F) for B1: replace [=varName] with M.get(varName)
    private String injectVariables(String json, Map<String, Object> M) {
        if (M == null || M.isEmpty() || json == null) return json;
        String result = json;
        for (Map.Entry<String, Object> entry : M.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("[=" + entry.getKey() + "]", entry.getValue().toString());
            }
        }
        return result;
    }

    // constraints(PA, rid) + transformConstraints(CR) + evaluate(PI)
    private boolean constraintsPass(JsonNode constraints) {
        if (!constraints.isArray() || constraints.isEmpty()) return true; // no constraints → always fires
        for (JsonNode c : constraints) {
            if (!evaluateConstraint(c)) return false;
        }
        return true;
    }

    private boolean evaluateConstraint(JsonNode c) {
        String operator = normalizeOperator(c.path("operator").asText(""));
        Object left = resolveOperand(c.path("leftOperand"));
        Object right = resolveOperand(c.path("rightOperand"));

        if (left == null || right == null || operator.isEmpty()) {
            log.warn("action=CONSTRAINT_INCOMPLETE constraint={}", c);
            return false;
        }
        return compare(left.toString(), operator, right.toString());
    }

    private Object resolveOperand(JsonNode operand) {
        if (operand.isMissingNode()) return null;

        if (operand.isTextual()) {
            // Built-in operand name
            return switch (operand.asText()) {
                case "dateTime", "odrl:dateTime" -> OffsetDateTime.now().toString();
                default -> operand.asText();
            };
        }

        if (operand.isObject()) {
            JsonNode value = operand.path("@value");
            if (!value.isMissingNode()) return value.asText();
        }

        return null;
    }

    private boolean compare(String left, String op, String right) {
        // Try OffsetDateTime
        try {
            OffsetDateTime l = OffsetDateTime.parse(left);
            OffsetDateTime r = OffsetDateTime.parse(right);
            return switch (op) {
                case "lt"   -> l.isBefore(r);
                case "lteq" -> !l.isAfter(r);
                case "eq"   -> l.isEqual(r);
                case "neq"  -> !l.isEqual(r);
                case "gt"   -> l.isAfter(r);
                case "gteq" -> !l.isBefore(r);
                default     -> false;
            };
        } catch (Exception ignored) {}

        // Try numeric
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return switch (op) {
                case "lt"   -> l < r;
                case "lteq" -> l <= r;
                case "eq"   -> l == r;
                case "neq"  -> l != r;
                case "gt"   -> l > r;
                case "gteq" -> l >= r;
                default     -> false;
            };
        } catch (Exception ignored) {}

        // String comparison
        int cmp = left.compareTo(right);
        return switch (op) {
            case "lt"   -> cmp < 0;
            case "lteq" -> cmp <= 0;
            case "eq"   -> cmp == 0;
            case "neq"  -> cmp != 0;
            case "gt"   -> cmp > 0;
            case "gteq" -> cmp >= 0;
            default     -> {
                log.warn("action=UNKNOWN_OPERATOR op={}", op);
                yield false;
            }
        };
    }

    private String normalizeOperator(String op) {
        if (op.contains(":")) op = op.substring(op.lastIndexOf(':') + 1);
        return op.toLowerCase();
    }

    private String extractAction(JsonNode rule) {
        JsonNode action = rule.path("action");
        if (action.isTextual()) {
            return normalizeOperator(action.asText());
        }
        if (action.isObject()) {
            JsonNode id = action.path("@id");
            if (!id.isMissingNode()) return normalizeOperator(id.asText());
        }
        return null;
    }
}
