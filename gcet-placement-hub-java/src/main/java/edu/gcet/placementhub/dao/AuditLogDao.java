package edu.gcet.placementhub.dao;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import edu.gcet.placementhub.models.AuditLog;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Append-only audit trail stored in the {@code audit_logs} collection
 * ({@code actorId} references {@code users._id}). Writes never throw so a logging hiccup
 * can never break a business operation.
 */
public class AuditLogDao {

    public static final String COLLECTION = "audit_logs";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    public void insertLog(MongoDatabase db, String action, String actorId, String actorUsername, String actorRole, String details) {
        try {
            col(db).insertOne(buildLog(action, actorId, actorUsername, actorRole, details, new Date()));
        } catch (MongoException ignored) {
            // Non-blocking audit log write
        }
    }

    public static Document buildLog(String action, String actorId, String actorUsername, String actorRole, String details, Date at) {
        return new Document("action", action)
                .append("actorId", toKey(actorId))
                .append("actorUsername", actorUsername != null ? actorUsername : "system")
                .append("actorRole", actorRole != null ? actorRole : "SYSTEM")
                .append("details", details)
                .append("createdAt", at != null ? at : new Date());
    }

    public List<AuditLog> findRecent(MongoDatabase db, int limit) {
        return findFiltered(db, "", "ALL", "ALL", "", "", null, limit);
    }

    public List<AuditLog> findFiltered(
            MongoDatabase db,
            String search,
            String actionFilter,
            String roleFilter,
            String fromDate,
            String toDate,
            String restrictedActorId,
            int limit
    ) {
        List<AuditLog> logs = new ArrayList<>();
        List<Bson> clauses = new ArrayList<>();

        if (restrictedActorId != null && !restrictedActorId.isBlank()) {
            clauses.add(refFilter("actorId", restrictedActorId.trim()));
        }
        if (actionFilter != null && !actionFilter.isBlank() && !"ALL".equalsIgnoreCase(actionFilter)) {
            clauses.add(Filters.regex("action", "^" + Pattern.quote(actionFilter.trim()) + "$", "i"));
        }
        if (roleFilter != null && !roleFilter.isBlank() && !"ALL".equalsIgnoreCase(roleFilter)) {
            clauses.add(Filters.regex("actorRole", "^" + Pattern.quote(roleFilter.trim()) + "$", "i"));
        }
        if (fromDate != null && !fromDate.isBlank()) {
            Date from = parseDay(fromDate);
            if (from != null) clauses.add(Filters.gte("createdAt", from));
        }
        if (toDate != null && !toDate.isBlank()) {
            Date to = parseDay(toDate);
            if (to != null) clauses.add(Filters.lt("createdAt", new Date(to.getTime() + 24L * 60 * 60 * 1000)));
        }
        if (search != null && !search.isBlank()) {
            String q = Pattern.quote(search.trim());
            clauses.add(Filters.or(
                    Filters.regex("action", q, "i"),
                    Filters.regex("actorUsername", q, "i"),
                    Filters.regex("actorRole", q, "i"),
                    Filters.regex("details", q, "i")));
        }

        Bson filter = clauses.isEmpty() ? new Document() : Filters.and(clauses);
        try {
            for (Document d : col(db).find(filter).sort(Sorts.descending("createdAt")).limit(Math.max(1, Math.min(limit, 1000)))) {
                logs.add(mapLog(d));
            }
        } catch (MongoException ignored) {
            // Return empty list if audit_logs unavailable
        }
        return logs;
    }

    public List<String> findDistinctActions(MongoDatabase db) {
        List<String> actions = new ArrayList<>();
        try {
            for (String a : col(db).distinct("action", String.class)) {
                if (a != null && !a.isBlank()) actions.add(a);
            }
            actions.sort(String::compareTo);
        } catch (MongoException ignored) {
        }
        return actions;
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    private static Date parseDay(String raw) {
        try {
            String clean = raw.trim();
            if (clean.length() > 10) clean = clean.substring(0, 10);
            return Date.from(LocalDate.parse(clean).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (Exception ex) {
            return null;
        }
    }

    public static AuditLog mapLog(Document d) {
        AuditLog log = new AuditLog();
        log.setId(id(d));
        log.setAction(str(d, "action"));
        log.setActorId(ref(d, "actorId"));
        log.setActorUsername(str(d, "actorUsername"));
        log.setActorRole(str(d, "actorRole"));
        log.setDetails(str(d, "details"));
        log.setCreatedAt(iso(d, "createdAt"));
        return log;
    }
}
