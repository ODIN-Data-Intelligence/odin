package com.odin.catalog.inventory.application.vocabulary;

/**
 * Utility for converting RDF IRIs to human-readable labels when no stored
 * prefLabel is available. Mirrors the frontend's {@code iriFragment} function.
 */
public final class IriUtils {

    private IriUtils() {}

    /**
     * Extracts the local name from an IRI and formats it for display.
     * <p>Examples:
     * <pre>
     *   "https://spec.edmcouncil.org/fibo/.../Customer"  → "Customer"
     *   "https://schema.org/LoanOrCredit"                → "Loan Or Credit"
     *   "http://creativecommons.org/licenses/by/4.0/"    → "4.0"
     *   "schema:Price"                                    → "Price"
     * </pre>
     */
    public static String humanize(String iri) {
        if (iri == null || iri.isBlank()) return iri;
        // Strip trailing slashes so "…/4.0/" gives "4.0" not ""
        String trimmed = iri.replaceAll("/+$", "");
        // Split on / # : and take the last non-empty segment
        String[] parts = trimmed.split("[/#:]");
        String fragment = "";
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) {
                fragment = parts[i];
                break;
            }
        }
        if (fragment.isBlank()) return iri;
        // Insert a space before each uppercase letter that follows a lowercase letter
        // "LoanOrCredit" → "Loan Or Credit"
        return fragment.replaceAll("([a-z])([A-Z])", "$1 $2").trim();
    }
}
