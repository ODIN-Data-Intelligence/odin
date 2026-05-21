package com.odin.catalog.harvest.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.harvest.domain.run.HarvestRun;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import com.odin.catalog.harvest.domain.source.SourceCredentials;
import com.odin.catalog.shared.models.common.NormalizedColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class GlueConnector implements HarvestConnector {

    private static final Logger log = LoggerFactory.getLogger(GlueConnector.class);
    private final ObjectMapper objectMapper;

    public GlueConnector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceType() {
        return "aws_glue";
    }

    @Override
    public boolean testConnection(HarvestSource source) {
        try (GlueClient client = buildClient(source)) {
            client.getDatabases(GetDatabasesRequest.builder().build());
            return true;
        } catch (Exception e) {
            log.warn("Glue connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Stream<HarvestEntity> harvest(HarvestRun run, HarvestSource source) {
        GlueClient client = buildClient(source);
        List<HarvestEntity> entities = new ArrayList<>();

        try {
            GetDatabasesRequest dbReq = GetDatabasesRequest.builder().build();
            GetDatabasesResponse dbRes = client.getDatabases(dbReq);

            for (Database db : dbRes.databaseList()) {
                String dbName = db.name();
                if (source.databaseName() != null && !source.databaseName().equals(dbName)) continue;

                GetTablesRequest tableReq = GetTablesRequest.builder().databaseName(dbName).build();
                GetTablesResponse tableRes = client.getTables(tableReq);

                for (Table table : tableRes.tableList()) {
                    if (isFiltered(table.name(), source.schemaFilter())) continue;

                    String sourceKey = dbName + "." + table.name();
                    String sourceUri = "glue://" + source.region() + "/" + sourceKey;

                    List<NormalizedColumn> columns = new ArrayList<>();
                    if (table.storageDescriptor() != null && table.storageDescriptor().columns() != null) {
                        int ordinal = 0;
                        for (Column col : table.storageDescriptor().columns()) {
                            columns.add(new NormalizedColumn(
                                col.name(), col.type(), false, false,
                                col.comment(), null, ordinal++
                            ));
                        }
                    }

                    String ddl = table.tableType() != null && table.tableType().equalsIgnoreCase("VIRTUAL_VIEW")
                        ? table.viewOriginalText() : null;

                    entities.add(new HarvestEntity(
                        sourceKey,
                        ddl != null ? HarvestEntityType.VIEW : HarvestEntityType.DATASET,
                        sourceUri,
                        table.name(),
                        table.description(),
                        null, null,
                        List.of(), List.of(),
                        columns, ddl,
                        objectMapper.valueToTree(table.toBuilder())
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error harvesting from Glue: {}", e.getMessage(), e);
        } finally {
            client.close();
        }

        log.info("Glue harvest discovered {} entities", entities.size());
        return entities.stream();
    }

    private GlueClient buildClient(HarvestSource source) {
        var builder = GlueClient.builder()
            .region(Region.of(source.region() != null ? source.region() : "us-east-1"));

        SourceCredentials creds = source.credentials();
        if (creds != null && creds.accessKeyId() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(creds.accessKeyId(), creds.secretAccessKey())
            ));
        }
        return builder.build();
    }

    private boolean isFiltered(String name, List<String> filter) {
        if (filter == null || filter.isEmpty()) return false;
        return filter.stream().noneMatch(f -> name.matches(f.replace("*", ".*")));
    }
}
