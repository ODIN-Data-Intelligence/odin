package com.odin.catalog.harvest.connector;

public record HarvestDistribution(
    String title,
    String downloadUrl,
    String accessUrl,
    String format,
    String mediaType
) {}
