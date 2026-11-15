package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import edu.gcet.placementhub.models.GctcAnnouncement;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the referenced {@code announcements} collection (GCTC circulars).
 *
 * <pre>
 * { _id, title, date, publishedAt, category, summary, link, important, attachmentName, postedBy, createdAt }
 * </pre>
 */
public class AnnouncementDao {

    public static final String COLLECTION = "announcements";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    public List<GctcAnnouncement> findAll(MongoDatabase db) {
        List<GctcAnnouncement> list = new ArrayList<>();
        for (Document d : col(db).find().sort(Sorts.descending("publishedAt"))) {
            list.add(mapAnnouncement(d));
        }
        return list;
    }

    public String insert(MongoDatabase db, GctcAnnouncement a, java.util.Date publishedAt) {
        Document d = toDocument(a, publishedAt);
        col(db).insertOne(d);
        return id(d);
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    public static GctcAnnouncement mapAnnouncement(Document d) {
        return new GctcAnnouncement(
                id(d),
                str(d, "title"),
                str(d, "date"),
                str(d, "category", "GENERAL"),
                str(d, "summary"),
                str(d, "link"),
                bool(d, "important", false),
                str(d, "attachmentName"),
                str(d, "postedBy")
        );
    }

    public static Document toDocument(GctcAnnouncement a, java.util.Date publishedAt) {
        return new Document("title", a.title())
                .append("date", a.date())
                .append("publishedAt", publishedAt != null ? publishedAt : now())
                .append("category", a.category())
                .append("summary", a.summary())
                .append("link", a.link())
                .append("important", a.important())
                .append("attachmentName", a.attachmentName())
                .append("postedBy", a.postedBy())
                .append("createdAt", now());
    }
}
