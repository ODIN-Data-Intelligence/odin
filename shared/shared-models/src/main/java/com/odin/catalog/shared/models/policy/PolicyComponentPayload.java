package com.odin.catalog.shared.models.policy;

/**
 * One ODRL policy piece carried in a DatasetChanged event.
 * policyJson is a target-free ODRL fragment; target is injected during assembly.
 */
public record PolicyComponentPayload(
    String pieceType,     // CLASSIFICATION, REGULATION, CONTRACTUAL
    String dimensionKey,  // e.g. CONFIDENTIAL, GDPR Data Protection
    String label,
    String policyJson     // target-free ODRL snippet
) {}
