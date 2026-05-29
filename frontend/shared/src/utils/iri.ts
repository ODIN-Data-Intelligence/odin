/**
 * Returns the human-readable label for an RDF resource using the priority:
 *   1. preferredLabel  – skos:prefLabel / rdfs:label stored on the concept
 *   2. description     – dcterms:description / skos:definition
 *   3. IRI fragment    – local name after the last / or # (never the full IRI)
 */
export function preferredLabel(
  iri: string,
  label?: string | null,
  description?: string | null,
): string {
  if (label && label.trim()) return label.trim();
  if (description && description.trim()) return description.trim();
  return iriFragment(iri);
}

/**
 * Extracts the local name from an IRI and formats it for display.
 * "https://spec.edmcouncil.org/fibo/.../Customer" → "Customer"
 * "https://schema.org/LoanOrCredit"               → "Loan Or Credit"
 */
export function iriFragment(iri: string): string {
  // Remove trailing slashes before extracting the local name so that
  // "http://creativecommons.org/licenses/by/4.0/" → "4.0" not ""
  const trimmed = iri.replace(/\/+$/, '');
  // Split on / # or : to handle both full IRIs and prefixed names (e.g. schema:Price)
  const parts = trimmed.split(/[/#:]/);
  const fragment = parts.filter(Boolean).pop() ?? iri;
  return fragment.replace(/([A-Z])/g, ' $1').trim();
}
