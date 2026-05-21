package com.odin.catalog.harvest.connector;

import com.odin.catalog.harvest.domain.source.HarvestSource;
import com.odin.catalog.harvest.domain.run.HarvestRun;

import java.util.stream.Stream;

/**
 * SPI for harvest connectors. Each source type implements this interface.
 * Implementations are Spring beans registered with @Component.
 */
public interface HarvestConnector {

    /** The source type this connector handles (e.g., "aws_glue", "snowflake"). */
    String sourceType();

    /** Validates connectivity — called by POST /api/v1/sources/{id}/test. */
    boolean testConnection(HarvestSource source);

    /** Streams raw+normalized entities discovered from the source. */
    Stream<HarvestEntity> harvest(HarvestRun run, HarvestSource source);
}
