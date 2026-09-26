package com.chengjing.platform.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable, owner-scoped records used by feature modules until their own schema is introduced. */
@Repository
public class PlatformRecordStore {
    private static final Set<String> KINDS = Set.of("account", "preparation", "interview", "assessment");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlatformRecordStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void put(String kind, String ownerId, String recordId, JsonNode payload) {
        check(kind, ownerId, recordId);
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException("Record payload must be an object");
        String json = payload.toString();
        if (json.length() > 1_000_000) throw new IllegalArgumentException("Record exceeds storage limit");
        int updated = jdbc.update("UPDATE domain_records SET payload_json=?, updated_at=CURRENT_TIMESTAMP WHERE kind=? AND owner_id=? AND record_id=?",
                json, kind, ownerId, recordId);
        if (updated == 0) jdbc.update("INSERT INTO domain_records(kind,owner_id,record_id,payload_json) VALUES(?,?,?,?)",
                kind, ownerId, recordId, json);
    }

    public Optional<JsonNode> find(String kind, String ownerId, String recordId) {
        check(kind, ownerId, recordId);
        return jdbc.query("SELECT payload_json FROM domain_records WHERE kind=? AND owner_id=? AND record_id=?",
                (rs, rowNum) -> read(rs.getString(1)), kind, ownerId, recordId).stream().findFirst();
    }

    public List<StoredRecord> list(String kind, String ownerId) {
        check(kind, ownerId, "list");
        return jdbc.query("SELECT record_id,payload_json FROM domain_records WHERE kind=? AND owner_id=? ORDER BY updated_at DESC, record_id",
                (rs, rowNum) -> new StoredRecord(rs.getString(1), read(rs.getString(2))), kind, ownerId);
    }

    public boolean delete(String kind, String ownerId, String recordId) {
        check(kind, ownerId, recordId);
        return jdbc.update("DELETE FROM domain_records WHERE kind=? AND owner_id=? AND record_id=?", kind, ownerId, recordId) > 0;
    }

    /** Called by account erasure after the identity module has verified the user. */
    public int deleteOwner(String ownerId) {
        check("account", ownerId, "delete");
        return jdbc.update("DELETE FROM domain_records WHERE owner_id=?", ownerId);
    }

    private JsonNode read(String json) {
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("Stored record is unreadable", e); }
    }

    private void check(String kind, String ownerId, String recordId) {
        if (!KINDS.contains(kind) || ownerId == null || ownerId.isBlank() || ownerId.length() > 128
                || recordId == null || recordId.isBlank() || recordId.length() > 128)
            throw new IllegalArgumentException("Invalid record scope");
    }

    public record StoredRecord(String id, JsonNode payload) {}
}
