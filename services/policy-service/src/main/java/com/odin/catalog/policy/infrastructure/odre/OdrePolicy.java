package com.odin.catalog.policy.infrastructure.odre;

public final class OdrePolicy {

    private final String json;

    private OdrePolicy(String json) {
        this.json = json;
    }

    public static OdrePolicy load(String policyJson) {
        if (policyJson == null || policyJson.isBlank()) {
            throw new IllegalArgumentException("Policy JSON must not be blank");
        }
        return new OdrePolicy(policyJson);
    }

    public String getJson() {
        return json;
    }
}
