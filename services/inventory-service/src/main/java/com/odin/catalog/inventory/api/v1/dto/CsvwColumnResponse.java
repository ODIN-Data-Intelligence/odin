package com.odin.catalog.inventory.api.v1.dto;

import com.odin.catalog.inventory.infrastructure.jpa.entity.CsvwColumnEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "A single column in a CSV-W physical schema")
public record CsvwColumnResponse(

    UUID id,
    UUID schemaId,
    Integer ordinal,
    String name,
    String datatype,
    String description,
    Boolean required,
    List<String> titles,
    String propertyUrl,
    Boolean virtual,
    Boolean suppressOutput,
    String lang,
    String defaultValue,
    String valueUrl,
    String aboutUrl,
    UUID logicalDataElementId

) {
    public static CsvwColumnResponse from(CsvwColumnEntity e) {
        return new CsvwColumnResponse(
            e.getId(), e.getSchemaId(), e.getOrdinal(), e.getName(),
            e.getDatatype(), e.getDescription(), e.getRequired(), e.getTitles(),
            e.getPropertyUrl(), e.getVirtual(), e.getSuppressOutput(),
            e.getLang(), e.getDefaultValue(), e.getValueUrl(), e.getAboutUrl(),
            e.getLogicalDataElementId()
        );
    }
}
