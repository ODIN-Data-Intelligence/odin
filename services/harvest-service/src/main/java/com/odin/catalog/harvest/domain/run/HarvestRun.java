package com.odin.catalog.harvest.domain.run;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HarvestRun(
    UUID id,
    UUID jobId,
    UUID sourceId,
    String status,
    String triggeredBy,
    OffsetDateTime startedAt,
    boolean fullRefresh
) {}
