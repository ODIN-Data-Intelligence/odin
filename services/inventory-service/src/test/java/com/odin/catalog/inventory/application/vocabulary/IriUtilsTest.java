package com.odin.catalog.inventory.application.vocabulary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IriUtilsTest {

    @Test
    void humanize_null_returnsNull() {
        assertThat(IriUtils.humanize(null)).isNull();
    }

    @Test
    void humanize_blank_returnsBlank() {
        assertThat(IriUtils.humanize("   ")).isEqualTo("   ");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "https://spec.edmcouncil.org/fibo/ontology/FBC/.../Customer, Customer",
        "https://schema.org/LoanOrCredit, Loan Or Credit",
        "http://creativecommons.org/licenses/by/4.0/, 4.0",
        "schema:Price, Price",
        "https://schema.org/Person, Person"
    })
    void humanize_variousIriFormats_returnReadableLabel(String iri, String expected) {
        assertThat(IriUtils.humanize(iri)).isEqualTo(expected);
    }

    @Test
    void humanize_camelCaseFragment_insertsSpaces() {
        assertThat(IriUtils.humanize("https://example.com/ontology#DataOwnershipPolicy"))
            .isEqualTo("Data Ownership Policy");
    }

    @Test
    void humanize_singleWordFragment_returnsFragment() {
        assertThat(IriUtils.humanize("https://example.com/ontology/Customer")).isEqualTo("Customer");
    }

    @Test
    void humanize_hashFragment_extractsAfterHash() {
        assertThat(IriUtils.humanize("http://www.w3.org/2004/02/skos/core#prefLabel"))
            .isEqualTo("pref Label");
    }

    @Test
    void humanize_multipleTrailingSlashes_stripsAndExtracts() {
        assertThat(IriUtils.humanize("https://example.com/v1/"))
            .isEqualTo("v1");
    }

    @Test
    void humanize_allSegmentsBlankAfterSplit_returnsOriginalIri() {
        // "///" → all segments blank after split on [/#:] → fragment stays "" → returns original
        assertThat(IriUtils.humanize("///")).isEqualTo("///");
    }
}
