package com.odin.catalog.harvest.connector;

import com.odin.catalog.harvest.domain.run.HarvestRun;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import com.odin.catalog.harvest.domain.source.SourceCredentials;
import com.odin.catalog.shared.models.common.NormalizedColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

@Component
public class SnowflakeConnector implements HarvestConnector {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeConnector.class);

    @Override
    public String sourceType() {
        return "snowflake";
    }

    @Override
    public boolean testConnection(HarvestSource source) {
        try (Connection conn = getConnection(source)) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.warn("Snowflake connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Stream<HarvestEntity> harvest(HarvestRun run, HarvestSource source) {
        List<HarvestEntity> entities = new ArrayList<>();
        try (Connection conn = getConnection(source)) {
            String db = source.databaseName();
            List<String> schemas = getSchemas(conn, db, source.schemaFilter());
            for (String schema : schemas) {
                entities.addAll(harvestSchema(conn, db, schema));
            }
        } catch (Exception e) {
            log.error("Error harvesting from Snowflake: {}", e.getMessage(), e);
        }
        return entities.stream();
    }

    private List<HarvestEntity> harvestSchema(Connection conn, String db, String schema) throws SQLException {
        List<HarvestEntity> entities = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SHOW TABLES IN SCHEMA IDENTIFIER(?)"
        )) {
            ps.setString(1, db + "." + schema);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("name");
                String tableKind = rs.getString("kind");
                String sourceKey = db + "." + schema + "." + tableName;
                String sourceUri = "snowflake://" + sourceKey;
                boolean isView = "VIEW".equalsIgnoreCase(tableKind);

                List<NormalizedColumn> columns = describeTable(conn, db, schema, tableName);

                String ddl = null;
                if (isView) {
                    try (PreparedStatement ddlPs = conn.prepareStatement(
                        "SELECT GET_DDL('VIEW', ?) AS ddl"
                    )) {
                        ddlPs.setString(1, sourceKey);
                        ResultSet ddlRs = ddlPs.executeQuery();
                        if (ddlRs.next()) ddl = ddlRs.getString("ddl");
                    }
                }

                entities.add(new HarvestEntity(
                    sourceKey,
                    isView ? HarvestEntityType.VIEW : HarvestEntityType.DATASET,
                    sourceUri, tableName, null, null, null,
                    List.of(), List.of(), List.of(), columns, ddl, null
                ));
            }
        }
        return entities;
    }

    private List<NormalizedColumn> describeTable(Connection conn, String db, String schema, String table) throws SQLException {
        List<NormalizedColumn> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "DESCRIBE TABLE IDENTIFIER(?)"
        )) {
            ps.setString(1, db + "." + schema + "." + table);
            ResultSet rs = ps.executeQuery();
            int ordinal = 0;
            while (rs.next()) {
                columns.add(new NormalizedColumn(
                    rs.getString("name"),
                    rs.getString("type"),
                    "N".equalsIgnoreCase(rs.getString("null?")),
                    false,
                    rs.getString("comment"),
                    null,
                    ordinal++
                ));
            }
        }
        return columns;
    }

    private List<String> getSchemas(Connection conn, String db, List<String> filter) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW SCHEMAS IN DATABASE " + db)) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (filter == null || filter.isEmpty() || filter.contains(name)) {
                    schemas.add(name);
                }
            }
        }
        return schemas;
    }

    private Connection getConnection(HarvestSource source) throws SQLException {
        SourceCredentials creds = source.credentials();
        Properties props = new Properties();
        props.put("db", source.databaseName());
        if (creds != null) {
            props.put("user", creds.username());
            props.put("password", creds.password());
        }
        return DriverManager.getConnection("jdbc:snowflake://" + source.baseUrl(), props);
    }
}
