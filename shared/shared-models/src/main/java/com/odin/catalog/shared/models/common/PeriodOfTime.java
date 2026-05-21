package com.odin.catalog.shared.models.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PeriodOfTime(
    String startDate,   // ISO-8601
    String endDate      // ISO-8601
) {}
