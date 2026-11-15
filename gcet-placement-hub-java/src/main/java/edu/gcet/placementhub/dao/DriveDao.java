package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import edu.gcet.placementhub.models.DriveRequiredSkill;
import edu.gcet.placementhub.models.PlacementDrive;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the {@code placement_drives} collection.
 *
 * <pre>
 * { _id, companyId: ObjectId (-> companies), companyName, companyTier   // denormalized for 1-query reads
 *   jobRole, jobDescription, salaryPackageLpa,
 *   eligibilityCriteria: { minCgpa, allowedBranches: [], maxBacklogs, targetGraduationYear },
 *   requiredSkills: [ { skillId, skillName, minProficiency, mandatory } ],
 *   driveDate, applicationDeadline, availablePositions, status, selectionProcess, createdAt }
 * </pre>
 * The former {@code drive_eligible_branches} and {@code drive_required_skills} join tables are
 * embedded arrays; the drives list page is now a single find() instead of three joins.
 */
public class DriveDao {

    public static final String COLLECTION = "placement_drives";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    public List<PlacementDrive> findAll(MongoDatabase db) {
        List<PlacementDrive> drives = new ArrayList<>();
        for (Document d : col(db).find().sort(Sorts.orderBy(Sorts.ascending("driveDate"), Sorts.descending("createdAt")))) {
            drives.add(mapDrive(d));
        }
        return drives;
    }

    public PlacementDrive findById(MongoDatabase db, String driveId) {
        if (driveId == null || driveId.isBlank()) return null;
        Document d = col(db).find(idFilter(driveId.trim())).first();
        return d == null ? null : mapDrive(d);
    }

    /** Full-text search over jobRole / companyName / jobDescription (text index). */
    public List<PlacementDrive> searchText(MongoDatabase db, String query) {
        List<PlacementDrive> list = new ArrayList<>();
        if (query == null || query.isBlank()) return findAll(db);
        for (Document d : col(db).find(Filters.text(query.trim()))) {
            list.add(mapDrive(d));
        }
        return list;
    }

    public String insertDrive(MongoDatabase db, PlacementDrive d) {
        enrichDenormalizedFields(db, d);
        Document doc = toDocument(d);
        col(db).insertOne(doc);
        d.setId(id(doc));
        db.getCollection(CompanyDao.COLLECTION).updateOne(idFilter(d.getCompanyId()), Updates.inc("totalDrivesConducted", 1));
        return d.getId();
    }

    public void updateDrive(MongoDatabase db, PlacementDrive d) {
        enrichDenormalizedFields(db, d);
        Document doc = toDocument(d);
        doc.remove("_id");
        doc.remove("createdAt");
        col(db).updateOne(idFilter(d.getId()), new Document("$set", doc));
    }

    public int countApplicationsForDrive(MongoDatabase db, String driveId) {
        return (int) db.getCollection(ApplicationDao.COLLECTION).countDocuments(refFilter("driveId", driveId));
    }

    /** Embedded branches and required skills vanish together with the drive document. */
    public void deleteDriveCascade(MongoDatabase db, String driveId) {
        col(db).deleteOne(idFilter(driveId));
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    /** Copies companyTier and skillName values into the document (extended-reference pattern). */
    private void enrichDenormalizedFields(MongoDatabase db, PlacementDrive d) {
        Document company = db.getCollection(CompanyDao.COLLECTION).find(idFilter(d.getCompanyId())).first();
        if (company != null) {
            if (d.getCompanyName() == null || d.getCompanyName().isBlank()) d.setCompanyName(str(company, "name"));
            d.setCompanyTier(str(company, "tier", "Standard IT"));
        }
        Map<String, String> skillNames = new HashMap<>();
        for (Document sk : db.getCollection(SkillDao.COLLECTION).find().projection(new Document("name", 1))) {
            skillNames.put(id(sk), str(sk, "name"));
        }
        for (DriveRequiredSkill r : d.getRequiredSkills()) {
            if (r != null && r.getSkillId() != null && (r.getSkillName() == null || r.getSkillName().isBlank())) {
                r.setSkillName(skillNames.getOrDefault(r.getSkillId(), r.getSkillId()));
            }
        }
    }

    // ------------------------------------------------------------------ mapping

    public static PlacementDrive mapDrive(Document d) {
        PlacementDrive pd = new PlacementDrive();
        pd.setId(id(d));
        pd.setCompanyId(ref(d, "companyId"));
        pd.setCompanyName(str(d, "companyName"));
        pd.setCompanyTier(str(d, "companyTier"));
        pd.setJobRole(str(d, "jobRole"));
        pd.setJobDescription(str(d, "jobDescription"));
        pd.setSalaryPackageLpa(dbl(d, "salaryPackageLpa", 0));
        Document ec = sub(d, "eligibilityCriteria");
        pd.setMinCgpa(dbl(ec, "minCgpa", 6.0));
        pd.setMaxBacklogsAllowed(intVal(ec, "maxBacklogs", 0));
        pd.setTargetGraduationYear(intVal(ec, "targetGraduationYear", 2026));
        pd.setEligibleBranches(strList(ec, "allowedBranches"));
        List<DriveRequiredSkill> reqs = new ArrayList<>();
        for (Document r : docList(d, "requiredSkills")) {
            DriveRequiredSkill rs = new DriveRequiredSkill(ref(r, "skillId"), intVal(r, "minProficiency", 3), bool(r, "mandatory", false));
            rs.setSkillName(str(r, "skillName"));
            reqs.add(rs);
        }
        pd.setRequiredSkills(reqs);
        pd.setDriveDate(isoDate(d, "driveDate"));
        pd.setApplicationDeadline(isoDate(d, "applicationDeadline"));
        pd.setAvailablePositions(intVal(d, "availablePositions", 1));
        pd.setStatus(str(d, "status", "OPEN"));
        pd.setSelectionProcess(str(d, "selectionProcess"));
        pd.setCreatedAt(iso(d, "createdAt"));
        return pd;
    }

    public static Document toDocument(PlacementDrive d) {
        Document doc = new Document();
        if (d.getId() != null && !d.getId().isBlank()) doc.append("_id", toKey(d.getId()));
        List<String> branches = new ArrayList<>();
        for (String b : d.getEligibleBranches()) {
            if (b != null && !b.isBlank()) branches.add(b.trim());
        }
        List<Document> reqs = new ArrayList<>();
        for (DriveRequiredSkill r : d.getRequiredSkills()) {
            if (r == null || r.getSkillId() == null || r.getSkillId().isBlank()) continue;
            reqs.add(new Document("skillId", toKey(r.getSkillId().trim()))
                    .append("skillName", r.getSkillName())
                    .append("minProficiency", Math.max(1, Math.min(5, r.getMinProficiency())))
                    .append("mandatory", r.isMandatory()));
        }
        return doc.append("companyId", toKey(d.getCompanyId()))
                .append("companyName", d.getCompanyName())
                .append("companyTier", nvl(d.getCompanyTier(), "Standard IT"))
                .append("jobRole", d.getJobRole())
                .append("jobDescription", nvl(d.getJobDescription(), ""))
                .append("salaryPackageLpa", d.getSalaryPackageLpa())
                .append("eligibilityCriteria", new Document("minCgpa", d.getMinCgpa())
                        .append("allowedBranches", branches)
                        .append("maxBacklogs", d.getMaxBacklogsAllowed())
                        .append("targetGraduationYear", d.getTargetGraduationYear() > 0 ? d.getTargetGraduationYear() : 2026))
                .append("requiredSkills", reqs)
                .append("driveDate", toDateOrToday(d.getDriveDate()))
                .append("applicationDeadline", toDateOrToday(d.getApplicationDeadline()))
                .append("availablePositions", Math.max(1, d.getAvailablePositions()))
                .append("status", nvl(d.getStatus(), "OPEN"))
                .append("selectionProcess", nvl(d.getSelectionProcess(), "Online Assessment -> Technical Interview -> HR"))
                .append("createdAt", d.getCreatedAt() != null ? toDateOrToday(d.getCreatedAt()) : now())
                .append("updatedAt", now());
    }
}
