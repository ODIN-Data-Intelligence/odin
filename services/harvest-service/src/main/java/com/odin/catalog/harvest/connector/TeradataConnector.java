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
public class TeradataConnector implements HarvestConnector {

    private static final Logger log = LoggerFactory.getLogger(TeradataConnector.class);

    @Override
    public String sourceType() {
        return "teradata";
    }

    @Override
    public boolean testConnection(HarvestSource source) {
        try (Connection conn = getConnection(source)) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.warn("Teradata connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Stream<HarvestEntity> harvest(HarvestRun run, HarvestSource source) {
        List<HarvestEntity> entities = new ArrayList<>();
        try (Connection conn = getConnection(source)) {
            entities.addAll(harvestTables(conn, source));
        } catch (Exception e) {
            log.error("Error harvesting from Teradata: {}", e.getMessage(), e);
        }
        return entities.stream();
    }

    private List<HarvestEntity> harvestTables(Connection conn, HarvestSource source) throws SQLException {
        List<HarvestEntity> entities = new ArrayList<>();

        String dbFilter = source.databaseName() != null
            ? "AND t.DataBaseName = '" + source.databaseName() + "'"
            : "";

        // Query DBC.TablesV for tables and views
        String sql = """
            SELECT t.DataBaseName, t.TableName, t.TableKind, t.CommentString
            FROM DBC.TablesV t
            WHERE t.TableKind IN ('T', 'V')
            """ + dbFilter;

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String dbName = rs.getString("DataBaseName").trim();
                String tableName = rs.getString("TableName").trim();
                String tableKind = rs.getString("TableKind").trim();
                String comment = rs.getString("CommentString");
                boolean isView = "V".equals(tableKind);

                String sourceKey = dbName + "." + tableName;
                String sourceUri = "teradata://" + source.baseUrl() + "/" + sourceKey;

                List<NormalizedColumn> columns = describeTable(conn, dbName, tableName);

                String ddl = null;
                if (isView) {
                    ddl = getViewDdl(conn, dbName, tableName);
                }

                entities.add(new HarvestEntity(
                    sourceKey,
                    isView ? HarvestEntityType.VIEW : HarvestEntityType.DATASET,
                    sourceUri, tableName, comment, null, null,
                    List.of(), List.of(), columns, ddl, null
                ));
            }
        }
        return entities;
    }

    private List<NormalizedColumn> describeTable(Connection conn, String db, String table) throws SQLException {
        List<NormalizedColumn> columns = new ArrayList<>();
        String sql = """
            SELECT ColumnName, ColumnType, Nullable, CommentString, ColumnId
            FROM DBC.ColumnsV
            WHERE DataBaseName = ? AND TableName = ?
            ORDER BY ColumnId
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, db);
            ps.setString(2, table);
            ResultSet rs = ps.executeQuery();
            int ordinal = 0;
            while (rs.next()) {
                columns.add(new NormalizedColumn(
                    rs.getString("ColumnName").trim(),
                    rs.getString("ColumnType").trim(),
                    "N".equalsIgnoreCase(rs.getString("Nullable")),
                    false,
                    rs.getString("CommentString"),
                    null,
                    ordinal++
                ));
            }
        }
        return columns;
    }

    private String getViewDdl(Connection conn, String db, String view) {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT RequestText FROM DBC.ShowSQL WHERE DatabaseName = ? AND TVMName = ?"
        )) {
            ps.setString(1, db);
            ps.setString(2, view);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("RequestText");
        } catch (Exception e) {
            log.warn("Could not retrieve DDL for {}.{}: {}", db, view, e.getMessage());
        }
        return null;
    }

    private Connection getConnection(HarvestSource source) throws SQLException {
        SourceCredentials creds = source.credentials();
        Properties props = new Properties();
        if (creds != null) {
            props.put("user", creds.username());
            props.put("password", creds.password());
        }
        return DriverManager.getConnection("jdbc:teradata://" + source.baseUrl(), props);
    }
}
