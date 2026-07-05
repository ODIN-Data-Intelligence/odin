package com.odin.catalog.lineage.api.v1.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CatalogLinkRequest(@NotNull UUID catalogResourceId) {}
