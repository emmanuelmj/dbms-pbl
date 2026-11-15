package edu.gcet.placementhub.database;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.Filters;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Small BSON <-> POJO mapping helpers shared by every DAO.
 *
 * Identity strategy: every document uses a native {@link ObjectId} `_id`; the Java models keep a
 * plain {@code String id} (the 24-hex representation) so none of the Swing UI signatures change.
 * Legacy string ids (e.g. "stu-<uuid>") imported from the old relational dump are still accepted.
 */
public final class BsonUtil {

    /** Case-insensitive collation used for unique email / username / rollNumber indexes and lookups. */
    public static final Collation CASE_INSENSITIVE = Collation.builder()
            .locale("en")
            .collationStrength(CollationStrength.SECONDARY)
            .build();

    private static final JsonWriterSettings PRETTY_RELAXED = JsonWriterSettings.builder()
            .indent(true)
            .outputMode(JsonMode.RELAXED)
            .build();

    private BsonUtil() {
    }

    // ------------------------------------------------------------------ ids

    public static String newId() {
        return new ObjectId().toHexString();
    }

    /** Converts a model id into the BSON key stored in `_id` or in a reference field. */
    public static Object toKey(String id) {
        if (id == null) return null;
        String clean = id.trim();
        return ObjectId.isValid(clean) ? new ObjectId(clean) : clean;
    }

    public static Bson idFilter(String id) {
        return Filters.eq("_id", toKey(id));
    }

    /** Reference filter tolerant of both ObjectId and legacy string references. */
    public static Bson refFilter(String field, String id) {
        if (id == null || id.isBlank()) return Filters.eq(field, null);
        Object key = toKey(id);
        if (key instanceof ObjectId) {
            return Filters.in(field, key, id.trim());
        }
        return Filters.eq(field, key);
    }

    public static String idToString(Object value) {
        if (value == null) return null;
        if (value instanceof ObjectId oid) return oid.toHexString();
        return String.valueOf(value);
    }

    public static String id(Document doc) {
        return doc == null ? null : idToString(doc.get("_id"));
    }

    public static String ref(Document doc, String field) {
        return doc == null ? null : idToString(doc.get(field));
    }

    // --------------------------------------------------------------- readers

    public static String str(Document doc, String key) {
        if (doc == null) return null;
        Object v = doc.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static String str(Document doc, String key, String def) {
        String v = str(doc, key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static int intVal(Document doc, String key, int def) {
        if (doc == null) return def;
        Object v = doc.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static double dbl(Document doc, String key, double def) {
        if (doc == null) return def;
        Object v = doc.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static Double dblOrNull(Document doc, String key) {
        if (doc == null) return null;
        Object v = doc.get(key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    public static boolean bool(Document doc, String key, boolean def) {
        if (doc == null) return def;
        Object v = doc.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof String s) return "true".equalsIgnoreCase(s) || "1".equals(s);
        return def;
    }

    public static Document sub(Document doc, String key) {
        if (doc == null) return new Document();
        Object v = doc.get(key);
        return v instanceof Document d ? d : new Document();
    }

    @SuppressWarnings("unchecked")
    public static List<Document> docList(Document doc, String key) {
        if (doc == null) return new ArrayList<>();
        Object v = doc.get(key);
        if (v instanceof List<?> list) {
            List<Document> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Document d) out.add(d);
            }
            return out;
        }
        return new ArrayList<>();
    }

    public static List<String> strList(Document doc, String key) {
        if (doc == null) return new ArrayList<>();
        Object v = doc.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) out.add(String.valueOf(o));
            }
        } else if (v instanceof String s && !s.isBlank()) {
            String cleaned = s.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            for (String part : cleaned.split(",")) {
                String item = part.trim().replace("\"", "").replace("'", "");
                if (!item.isEmpty()) out.add(item);
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- dates

    /** Formats a BSON date (or ISO string) field as an ISO-8601 instant string used by the models. */
    public static String iso(Document doc, String key) {
        if (doc == null) return null;
        Object v = doc.get(key);
        if (v instanceof Date d) return d.toInstant().toString();
        if (v instanceof String s && !s.isBlank()) return s;
        return null;
    }

    /** Formats a BSON date field as yyyy-MM-dd (used for drive dates / deadlines). */
    public static String isoDate(Document doc, String key) {
        if (doc == null) return null;
        Object v = doc.get(key);
        if (v instanceof Date d) return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString();
        if (v instanceof String s && !s.isBlank()) return s.length() >= 10 ? s.substring(0, 10) : s;
        return null;
    }

    public static Date now() {
        return new Date();
    }

    /** Parses an ISO instant or yyyy-MM-dd string into a BSON date; null when unparseable. */
    public static Date toDate(String isoOrDate) {
        if (isoOrDate == null || isoOrDate.isBlank()) return null;
        String s = isoOrDate.trim();
        try {
            return Date.from(Instant.parse(s));
        } catch (Exception ignored) {
        }
        try {
            String day = s.length() >= 10 ? s.substring(0, 10) : s;
            return Date.from(LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Date toDateOrToday(String isoOrDate) {
        Date d = toDate(isoOrDate);
        return d != null ? d : Date.from(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    // ----------------------------------------------------------------- misc

    public static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public static String nvl(String s, String def) {
        return (s != null && !s.isBlank()) ? s.trim() : def;
    }

    /** Pretty relaxed Extended JSON for the Live Schema Inspector and logs. */
    public static String prettyJson(Document doc) {
        return doc == null ? "null" : doc.toJson(PRETTY_RELAXED);
    }

    public static String prettyJson(List<? extends Bson> pipeline) {
        List<BsonDocument> stages = new ArrayList<>();
        for (Bson stage : pipeline) {
            stages.add(toBsonDocument(stage));
        }
        return new BsonDocument("pipeline", new BsonArray(stages)).toJson(PRETTY_RELAXED);
    }

    public static BsonDocument toBsonDocument(Bson bson) {
        return bson.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    /** Converts an aggregation pipeline into plain Documents (needed for runCommand / explain). */
    public static List<Document> toDocuments(List<? extends Bson> pipeline) {
        List<Document> out = new ArrayList<>();
        for (Bson stage : pipeline) {
            out.add(Document.parse(toBsonDocument(stage).toJson()));
        }
        return out;
    }
}
