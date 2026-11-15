package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import edu.gcet.placementhub.models.Application;
import edu.gcet.placementhub.models.ApplicationStage;
import edu.gcet.placementhub.models.StageHistoryEntry;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the {@code applications} collection.
 *
 * <pre>
 * { _id, studentId: ObjectId (-> students), driveId: ObjectId (-> placement_drives), companyId: ObjectId,
 *   companyName, jobRole,
 *   studentSnapshot: { rollNumber, fullName, branch, cgpa },          // extended reference
 *   currentStage, appliedAt, finalOfferLpa, rejectionReason,
 *   stageHistory: [ { stage, updatedBy, timestamp, remarks } ]          // immutable workflow trail
 * }
 * </pre>
 * Moving an application to the next stage is now ONE atomic document update ($set + $push) instead
 * of an UPDATE plus an INSERT into {@code application_stage_history} wrapped in a SQL transaction.
 * Reads use a {@code $lookup} to refresh the student snapshot (name / branch / CGPA may change).
 */
public class ApplicationDao {

    public static final String COLLECTION = "applications";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    /** Aggregation: role-scoped $match -> $lookup students -> $sort appliedAt desc. */
    public static List<Bson> scopedPipeline(User user) {
        List<Bson> pipeline = new ArrayList<>();
        Bson match = scopeFilter(user);
        if (match != null) pipeline.add(Aggregates.match(match));
        pipeline.add(Aggregates.lookup(StudentDao.COLLECTION, "studentId", "_id", "student"));
        pipeline.add(Aggregates.unwind("$student", new com.mongodb.client.model.UnwindOptions().preserveNullAndEmptyArrays(true)));
        pipeline.add(Aggregates.sort(Sorts.descending("appliedAt")));
        return pipeline;
    }

    private static Bson scopeFilter(User user) {
        if (user != null && user.getRole() == UserRole.STUDENT && user.getStudentRollNumber() != null && !user.getStudentRollNumber().isBlank()) {
            return Filters.regex("studentSnapshot.rollNumber", "^" + java.util.regex.Pattern.quote(user.getStudentRollNumber().trim()) + "$", "i");
        }
        if (user != null && user.getRole() == UserRole.COMPANY && user.getCompanyId() != null && !user.getCompanyId().isBlank()) {
            return refFilter("companyId", user.getCompanyId().trim());
        }
        return null;
    }

    public List<Application> findAllForUser(MongoDatabase db, User user) {
        List<Application> apps = new ArrayList<>();
        for (Document d : col(db).aggregate(scopedPipeline(user))) {
            apps.add(mapApplication(d));
        }
        return apps;
    }

    public Application findById(MongoDatabase db, String appId) {
        if (appId == null || appId.isBlank()) return null;
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(idFilter(appId.trim())));
        pipeline.add(Aggregates.lookup(StudentDao.COLLECTION, "studentId", "_id", "student"));
        pipeline.add(Aggregates.unwind("$student", new com.mongodb.client.model.UnwindOptions().preserveNullAndEmptyArrays(true)));
        Document d = col(db).aggregate(pipeline).first();
        return d == null ? null : mapApplication(d);
    }

    public Application findByStudentAndDrive(MongoDatabase db, String studentId, String driveId) {
        Document d = col(db).find(Filters.and(refFilter("studentId", studentId), refFilter("driveId", driveId))).first();
        return d == null ? null : mapApplication(d);
    }

    public String insertApplicationWithHistory(
            MongoDatabase db,
            String appId,
            String driveId,
            String studentId,
            String companyName,
            String jobRole,
            String actorFullName
    ) {
        Document student = db.getCollection(StudentDao.COLLECTION).find(idFilter(studentId)).first();
        Document drive = db.getCollection(DriveDao.COLLECTION).find(idFilter(driveId)).first();
        Document doc = new Document();
        if (appId != null && !appId.isBlank()) doc.append("_id", toKey(appId));
        doc.append("studentId", toKey(studentId))
                .append("driveId", toKey(driveId))
                .append("companyId", drive != null ? drive.get("companyId") : null)
                .append("companyName", companyName)
                .append("jobRole", jobRole)
                .append("studentSnapshot", snapshotOf(student))
                .append("currentStage", ApplicationStage.APPLICATION_SUBMITTED.name())
                .append("appliedAt", now())
                .append("finalOfferLpa", null)
                .append("rejectionReason", null)
                .append("stageHistory", List.of(historyEntry(ApplicationStage.APPLICATION_SUBMITTED, actorFullName,
                        "Application submitted via GCET Placement Hub")));
        col(db).insertOne(doc);
        return id(doc);
    }

    public void reapplyWithdrawnApplication(MongoDatabase db, String appId, String actorFullName) {
        col(db).updateOne(idFilter(appId), Updates.combine(
                Updates.set("currentStage", ApplicationStage.APPLICATION_SUBMITTED.name()),
                Updates.set("appliedAt", now()),
                Updates.set("rejectionReason", null),
                Updates.push("stageHistory", historyEntry(ApplicationStage.APPLICATION_SUBMITTED, actorFullName,
                        "Re-submitted application after withdrawal"))));
    }

    /**
     * Atomic stage transition: $set currentStage (+ offer / rejection fields) and $push the trail entry
     * in a single updateOne; then update the student's placement status when relevant.
     */
    public void updateApplicationStage(
            MongoDatabase db,
            String appId,
            String studentId,
            String companyId,
            ApplicationStage nextStage,
            Double offerLpa,
            String rejectionReason,
            String notes,
            String actorFullName
    ) {
        String effectiveNotes = (notes != null && !notes.isBlank()) ? notes : "Moved to " + nextStage.getLabel();
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set("currentStage", nextStage.name()));
        if (nextStage.isOfferOrSelected() && offerLpa != null) {
            updates.add(Updates.set("finalOfferLpa", offerLpa));
        }
        if (nextStage == ApplicationStage.REJECTED) {
            updates.add(Updates.set("rejectionReason", rejectionReason != null && !rejectionReason.isBlank() ? rejectionReason : notes));
        }
        updates.add(Updates.push("stageHistory", historyEntry(nextStage, actorFullName, effectiveNotes)));
        col(db).updateOne(idFilter(appId), Updates.combine(updates));

        MongoCollection<Document> students = db.getCollection(StudentDao.COLLECTION);
        if (nextStage.isOfferOrSelected()) {
            List<Bson> stu = new ArrayList<>();
            stu.add(Updates.set("placementStatus", "PLACED"));
            if (companyId != null && !companyId.isBlank()) stu.add(Updates.set("placedCompanyId", toKey(companyId)));
            if (offerLpa != null) stu.add(Updates.set("placedPackageLpa", offerLpa));
            stu.add(Updates.set("updatedAt", now()));
            students.updateOne(idFilter(studentId), Updates.combine(stu));
        } else if (nextStage == ApplicationStage.SHORTLISTED) {
            students.updateOne(Filters.and(idFilter(studentId), Filters.eq("placementStatus", "UNPLACED")),
                    Updates.set("placementStatus", "SHORTLISTED"));
        }
    }

    public void withdrawApplication(MongoDatabase db, String appId, String actorFullName) {
        col(db).updateOne(idFilter(appId), Updates.combine(
                Updates.set("currentStage", ApplicationStage.WITHDRAWN.name()),
                Updates.push("stageHistory", historyEntry(ApplicationStage.WITHDRAWN, actorFullName, "Withdrawn by candidate"))));
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    public static Document historyEntry(ApplicationStage stage, String updatedBy, String remarks) {
        return new Document("stage", stage.name())
                .append("updatedBy", updatedBy != null && !updatedBy.isBlank() ? updatedBy : "Placement Cell")
                .append("timestamp", now())
                .append("remarks", remarks != null ? remarks : "");
    }

    public static Document snapshotOf(Document student) {
        if (student == null) return new Document();
        Document am = sub(student, "academicMetrics");
        return new Document("rollNumber", str(student, "rollNumber"))
                .append("fullName", str(student, "fullName"))
                .append("branch", str(student, "branch"))
                .append("cgpa", dbl(am, "cgpa", 0));
    }

    // ------------------------------------------------------------------ mapping

    public static Application mapApplication(Document d) {
        Application a = new Application();
        a.setId(id(d));
        a.setDriveId(ref(d, "driveId"));
        a.setStudentId(ref(d, "studentId"));
        a.setCompanyId(ref(d, "companyId"));
        a.setCompanyName(str(d, "companyName"));
        a.setJobRole(str(d, "jobRole"));

        // Prefer the live $lookup'd student; fall back to the embedded snapshot
        Document live = d.get("student") instanceof Document sd ? sd : null;
        Document snap = sub(d, "studentSnapshot");
        if (live != null) {
            a.setStudentRollNumber(str(live, "rollNumber"));
            a.setStudentName(str(live, "fullName"));
            a.setStudentBranch(str(live, "branch"));
            a.setStudentCgpa(dbl(sub(live, "academicMetrics"), "cgpa", dbl(snap, "cgpa", 0)));
        } else {
            a.setStudentRollNumber(str(snap, "rollNumber"));
            a.setStudentName(str(snap, "fullName"));
            a.setStudentBranch(str(snap, "branch"));
            a.setStudentCgpa(dbl(snap, "cgpa", 0));
        }

        a.setCurrentStage(ApplicationStage.fromString(str(d, "currentStage")));
        a.setAppliedAt(iso(d, "appliedAt"));
        a.setFinalOfferLpa(dblOrNull(d, "finalOfferLpa"));
        a.setRejectionReason(str(d, "rejectionReason"));

        List<StageHistoryEntry> history = new ArrayList<>();
        for (Document h : docList(d, "stageHistory")) {
            StageHistoryEntry e = new StageHistoryEntry();
            e.setStage(ApplicationStage.fromString(str(h, "stage")));
            e.setTimestamp(iso(h, "timestamp"));
            e.setUpdatedBy(str(h, "updatedBy"));
            e.setNotes(str(h, "remarks"));
            history.add(e);
        }
        a.setStageHistory(history);
        return a;
    }
}
