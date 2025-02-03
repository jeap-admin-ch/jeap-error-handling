package db.migration.common;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class V5_0_0__MigrateCausingEventIdsToBeUnique extends BaseJavaMigration {

    @Override
    public void migrate(Context context) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));

        List<String> duplicatedIds = findDuplicateCausingEventIds(jdbcTemplate);
        for (String id : duplicatedIds) {
            fixDuplicateId(id, jdbcTemplate);
        }
    }

    private List<String> findDuplicateCausingEventIds(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT metadata_id FROM causing_event GROUP BY metadata_id HAVING COUNT(metadata_id) > 1",
                String.class);
    }

    private void fixDuplicateId(String causingEventMetadataId, JdbcTemplate jdbcTemplate) {
        // 1. Select all rows for this causing event ID from causing_event
        List<UUID> duplicateCausingEventEntityIds = findDuplicateCausingEventIds(causingEventMetadataId, jdbcTemplate);

        // 2. Set all errors to reference the first causing event
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        updateErrorsToReferenceFirstCausingEvent(duplicateCausingEventEntityIds, namedParameterJdbcTemplate);

        // 3. Delete all but the first causing event
        deleteDuplicateCausingEvents(duplicateCausingEventEntityIds, namedParameterJdbcTemplate);
    }

    private void updateErrorsToReferenceFirstCausingEvent(List<UUID> duplicateCausingEventEntityIds, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        UUID firstEntityId = duplicateCausingEventEntityIds.get(0);
        SqlParameterSource parameters = new MapSqlParameterSource(Map.of(
                "duplicateCausingEventEntityIds", duplicateCausingEventEntityIds,
                "firstEntityId", firstEntityId));
        namedParameterJdbcTemplate.update(
                "UPDATE error SET causing_event_id = :firstEntityId WHERE causing_event_id IN (:duplicateCausingEventEntityIds)", parameters);
    }

    private List<UUID> findDuplicateCausingEventIds(String causingEventMetadataId, JdbcTemplate jdbcTemplate) {
        List<UUID> duplicateCausingEventEntityIds = jdbcTemplate.queryForList(
                "SELECT id FROM causing_event WHERE metadata_id = ?", UUID.class, causingEventMetadataId);
        return duplicateCausingEventEntityIds;
    }

    private void deleteDuplicateCausingEvents(List<UUID> duplicateCausingEventEntityIds, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        List<UUID> idsToDelete = duplicateCausingEventEntityIds.subList(1, duplicateCausingEventEntityIds.size());
        SqlParameterSource deleteParameters = new MapSqlParameterSource(Map.of(
                "idsToDelete", idsToDelete));
        namedParameterJdbcTemplate.update("DELETE FROM causing_event WHERE id IN (:idsToDelete)", deleteParameters);
    }
}
