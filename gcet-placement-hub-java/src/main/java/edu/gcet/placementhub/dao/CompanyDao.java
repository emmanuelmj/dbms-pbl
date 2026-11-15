package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import edu.gcet.placementhub.models.Company;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the referenced {@code companies} collection.
 *
 * <pre>
 * { _id, name, industry, website, location, description, tier,
 *   hrContact: { name, email, phone },
 *   status, approvalStatus, approvedByAdminId: ObjectId, approvedByName, approvedAt, rejectionReason,
 *   totalDrivesConducted, createdAt }
 * </pre>
 * Companies keep an independent lifecycle (many drives reference one company), so they are
 * referenced by ObjectId from {@code placement_drives} and {@code users} rather than embedded.
 * A text index on name/industry/description powers the directory search.
 */
public class CompanyDao {

    public static final String COLLECTION = "companies";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    public List<Company> findAll(MongoDatabase db) {
        List<Company> list = new ArrayList<>();
        for (Document d : col(db).find().sort(Sorts.descending("createdAt"))) {
            list.add(mapCompany(d));
        }
        return list;
    }

    public Company findById(MongoDatabase db, String companyId) {
        if (companyId == null || companyId.isBlank()) return null;
        Document d = col(db).find(idFilter(companyId.trim())).first();
        return d == null ? null : mapCompany(d);
    }

    public Company findByName(MongoDatabase db, String name) {
        Document d = col(db).find(Filters.eq("name", nvl(name, ""))).collation(CASE_INSENSITIVE).first();
        return d == null ? null : mapCompany(d);
    }

    /** Full-text search using the compound text index (name, industry, description). */
    public List<Company> searchText(MongoDatabase db, String query) {
        List<Company> list = new ArrayList<>();
        if (query == null || query.isBlank()) return findAll(db);
        for (Document d : col(db).find(Filters.text(query.trim()))) {
            list.add(mapCompany(d));
        }
        return list;
    }

    public boolean existsByName(MongoDatabase db, String name, String excludeId) {
        Bson filter = Filters.eq("name", nvl(name, ""));
        if (excludeId != null && !excludeId.isBlank()) {
            filter = Filters.and(filter, Filters.ne("_id", toKey(excludeId.trim())));
        }
        return col(db).find(filter).collation(CASE_INSENSITIVE).projection(new Document("_id", 1)).first() != null;
    }

    public String insertCompany(MongoDatabase db, Company c, String adminId, String adminName) {
        String approval = nvl(c.getApprovalStatus(), "ACTIVE");
        c.setApprovalStatus(approval);
        c.setStatus(nvl(c.getStatus(), approval));
        c.setApprovedByAdminId(adminId);
        c.setApprovedByName(adminName);
        if (adminId != null) c.setApprovedAt(java.time.Instant.now().toString());
        Document d = toDocument(c);
        col(db).insertOne(d);
        c.setId(id(d));
        return c.getId();
    }

    public void updateCompany(MongoDatabase db, Company c) {
        col(db).updateOne(idFilter(c.getId()), Updates.combine(
                Updates.set("name", c.getName()),
                Updates.set("industry", nvl(c.getIndustry(), "Information Technology")),
                Updates.set("website", nvl(c.getWebsite(), "")),
                Updates.set("location", nvl(c.getLocation(), "")),
                Updates.set("description", nvl(c.getDescription(), "")),
                Updates.set("hrContact.name", nvl(c.getHrName(), "")),
                Updates.set("hrContact.email", nvl(c.getHrEmail(), "")),
                Updates.set("hrContact.phone", nvl(c.getHrPhone(), "")),
                Updates.set("tier", nvl(c.getTier(), "Standard IT")),
                Updates.set("updatedAt", now())));
        // Keep the denormalized company name/tier on drives in sync
        db.getCollection(DriveDao.COLLECTION).updateMany(refFilter("companyId", c.getId()), Updates.combine(
                Updates.set("companyName", c.getName()),
                Updates.set("companyTier", nvl(c.getTier(), "Standard IT"))));
    }

    public void updateApprovalStatus(
            MongoDatabase db,
            String companyId,
            String approvalStatus,
            String reason,
            String adminId,
            String adminName
    ) {
        col(db).updateOne(idFilter(companyId), Updates.combine(
                Updates.set("status", approvalStatus),
                Updates.set("approvalStatus", approvalStatus),
                Updates.set("rejectionReason", reason),
                Updates.set("approvedByAdminId", toKey(adminId)),
                Updates.set("approvedByName", adminName),
                Updates.set("approvedAt", now()),
                Updates.set("updatedAt", now())));
    }

    public int countDrivesForCompany(MongoDatabase db, String companyId) {
        return (int) db.getCollection(DriveDao.COLLECTION).countDocuments(refFilter("companyId", companyId));
    }

    public void deleteCompany(MongoDatabase db, String companyId) {
        col(db).deleteOne(idFilter(companyId));
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    // ------------------------------------------------------------------ mapping

    public static Company mapCompany(Document d) {
        Company c = new Company();
        c.setId(id(d));
        c.setName(str(d, "name"));
        c.setIndustry(str(d, "industry"));
        c.setWebsite(str(d, "website"));
        c.setLocation(str(d, "location"));
        c.setDescription(str(d, "description"));
        Document hr = sub(d, "hrContact");
        c.setHrName(str(hr, "name"));
        c.setHrEmail(str(hr, "email"));
        c.setHrPhone(str(hr, "phone"));
        c.setTier(str(d, "tier", "Standard IT"));
        String approvalStatus = str(d, "approvalStatus");
        String status = str(d, "status");
        c.setStatus(status != null && !status.isBlank() ? status : (approvalStatus != null ? approvalStatus : "ACTIVE"));
        c.setApprovalStatus(approvalStatus != null ? approvalStatus : c.getStatus());
        c.setApprovedByAdminId(ref(d, "approvedByAdminId"));
        c.setApprovedByName(str(d, "approvedByName"));
        c.setRejectionReason(str(d, "rejectionReason"));
        c.setTotalDrivesConducted(intVal(d, "totalDrivesConducted", 0));
        c.setApprovedAt(iso(d, "approvedAt"));
        c.setCreatedAt(iso(d, "createdAt"));
        return c;
    }

    public static Document toDocument(Company c) {
        Document d = new Document();
        if (c.getId() != null && !c.getId().isBlank()) d.append("_id", toKey(c.getId()));
        return d.append("name", c.getName())
                .append("industry", nvl(c.getIndustry(), "Information Technology"))
                .append("website", nvl(c.getWebsite(), ""))
                .append("location", nvl(c.getLocation(), ""))
                .append("description", nvl(c.getDescription(), ""))
                .append("hrContact", new Document("name", nvl(c.getHrName(), ""))
                        .append("email", nvl(c.getHrEmail(), ""))
                        .append("phone", nvl(c.getHrPhone(), "")))
                .append("tier", nvl(c.getTier(), "Standard IT"))
                .append("status", nvl(c.getStatus(), "ACTIVE"))
                .append("approvalStatus", nvl(c.getApprovalStatus(), "ACTIVE"))
                .append("approvedByAdminId", toKey(c.getApprovedByAdminId()))
                .append("approvedByName", c.getApprovedByName())
                .append("approvedAt", toDate(c.getApprovedAt()))
                .append("rejectionReason", c.getRejectionReason())
                .append("totalDrivesConducted", c.getTotalDrivesConducted())
                .append("createdAt", c.getCreatedAt() != null ? toDateOrToday(c.getCreatedAt()) : now())
                .append("updatedAt", now());
    }
}
