package edu.gcet.placementhub.database;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema governance for the document model, applied idempotently on every startup:
 *
 * <ol>
 *   <li>{@code $jsonSchema} validators (BSON types, required fields, enum states, CGPA 0.0-10.0,
 *       proficiency 1-5) on the four core collections: students, placement_drives, applications, users.</li>
 *   <li>Unique / compound / multikey / text indexes that back every hot query path.</li>
 * </ol>
 *
 * MongoDB is schema-flexible, not schema-less: validation is applied server-side with
 * {@code validationLevel: moderate} (existing documents are not rejected retroactively) and
 * {@code validationAction: error} (invalid inserts / updates are refused).
 */
public final class MongoSchemaInitializer {

    private MongoSchemaInitializer() {
    }

    // ------------------------------------------------------------- validators

    public static final String STUDENTS_SCHEMA = """
            {
              "bsonType": "object",
              "title": "GCET Student Profile (embedded skills / projects / certifications / internships)",
              "required": ["rollNumber", "fullName", "email", "branch", "academicMetrics", "approvalStatus", "placementStatus", "createdAt"],
              "properties": {
                "rollNumber": { "bsonType": "string", "minLength": 4, "description": "JNTUH-style roll number, e.g. 22R11A0501" },
                "fullName":   { "bsonType": "string", "minLength": 2 },
                "email":      { "bsonType": "string", "pattern": "^.+@.+\\\\..+$" },
                "branch":     { "bsonType": "string" },
                "userId":     { "bsonType": ["objectId", "string", "null"], "description": "Reference -> users._id" },
                "academicMetrics": {
                  "bsonType": "object",
                  "required": ["cgpa", "backlogs", "graduationYear"],
                  "properties": {
                    "cgpa":             { "bsonType": ["double", "int", "long", "decimal"], "minimum": 0.0, "maximum": 10.0, "description": "CGPA on a 10-point scale" },
                    "tenthPercentage":  { "bsonType": ["double", "int", "long", "decimal"], "minimum": 0, "maximum": 100 },
                    "interPercentage":  { "bsonType": ["double", "int", "long", "decimal"], "minimum": 0, "maximum": 100 },
                    "backlogs":         { "bsonType": ["int", "long"], "minimum": 0 },
                    "yearOfStudy":      { "bsonType": ["int", "long"], "minimum": 1, "maximum": 6 },
                    "academicYear":     { "bsonType": ["int", "long"], "minimum": 1, "maximum": 6 },
                    "graduationYear":   { "bsonType": ["int", "long"], "minimum": 2000, "maximum": 2100 }
                  }
                },
                "approvalStatus":  { "enum": ["PENDING_APPROVAL", "APPROVED", "REJECTED"] },
                "placementStatus": { "enum": ["UNPLACED", "SHORTLISTED", "PLACED", "HIGHER_STUDIES", "OPTED_OUT"] },
                "placedPackageLpa": { "bsonType": ["double", "int", "long", "decimal", "null"], "minimum": 0 },
                "skills": {
                  "bsonType": "array",
                  "items": {
                    "bsonType": "object",
                    "required": ["skillId", "proficiency"],
                    "properties": {
                      "skillId":           { "bsonType": ["objectId", "string"], "description": "Reference -> skills._id" },
                      "skillName":         { "bsonType": ["string", "null"] },
                      "proficiency":       { "bsonType": ["int", "long"], "minimum": 1, "maximum": 5 },
                      "verifiedByFaculty": { "bsonType": "bool" }
                    }
                  }
                },
                "projects":       { "bsonType": "array" },
                "certifications": { "bsonType": "array" },
                "internships":    { "bsonType": "array" },
                "createdAt":      { "bsonType": "date" }
              }
            }
            """;

    public static final String USERS_SCHEMA = """
            {
              "bsonType": "object",
              "title": "GCET Placement Hub login identity (RBAC)",
              "required": ["username", "email", "fullName", "role", "passwordHash", "status", "isActive", "createdAt"],
              "properties": {
                "username":     { "bsonType": "string", "minLength": 2 },
                "email":        { "bsonType": "string", "pattern": "^.+@.+\\\\..+$" },
                "fullName":     { "bsonType": "string" },
                "role":         { "enum": ["PRINCIPAL", "CHAIRMAN", "VICE_CHAIRMAN", "ADMIN", "PLACEMENT_OFFICER", "FACULTY", "STUDENT", "COMPANY"] },
                "status":       { "enum": ["ACTIVE", "APPROVED", "PENDING_APPROVAL", "PENDING", "REJECTED", "INACTIVE"] },
                "isActive":     { "bsonType": "bool" },
                "mustChangePassword": { "bsonType": "bool" },
                "passwordHash": { "bsonType": "string", "minLength": 20 },
                "companyId":    { "bsonType": ["objectId", "string", "null"], "description": "Reference -> companies._id" },
                "authorizedDepartments": { "bsonType": "array", "items": { "bsonType": "string" } },
                "createdAt":    { "bsonType": "date" }
              }
            }
            """;

    public static final String DRIVES_SCHEMA = """
            {
              "bsonType": "object",
              "title": "Campus placement drive (embedded eligibilityCriteria + requiredSkills)",
              "required": ["companyId", "companyName", "jobRole", "eligibilityCriteria", "status", "driveDate", "applicationDeadline", "createdAt"],
              "properties": {
                "companyId":   { "bsonType": ["objectId", "string"], "description": "Reference -> companies._id" },
                "companyName": { "bsonType": "string", "description": "Denormalized for single-query reads" },
                "companyTier": { "bsonType": ["string", "null"] },
                "jobRole":     { "bsonType": "string", "minLength": 2 },
                "salaryPackageLpa":   { "bsonType": ["double", "int", "long", "decimal"], "minimum": 0 },
                "availablePositions": { "bsonType": ["int", "long"], "minimum": 1 },
                "eligibilityCriteria": {
                  "bsonType": "object",
                  "required": ["minCgpa", "allowedBranches", "maxBacklogs"],
                  "properties": {
                    "minCgpa":              { "bsonType": ["double", "int", "long", "decimal"], "minimum": 0.0, "maximum": 10.0 },
                    "allowedBranches":      { "bsonType": "array", "minItems": 1, "items": { "bsonType": "string" } },
                    "maxBacklogs":          { "bsonType": ["int", "long"], "minimum": 0 },
                    "targetGraduationYear": { "bsonType": ["int", "long"], "minimum": 2000, "maximum": 2100 }
                  }
                },
                "requiredSkills": {
                  "bsonType": "array",
                  "items": {
                    "bsonType": "object",
                    "required": ["skillId", "minProficiency", "mandatory"],
                    "properties": {
                      "skillId":        { "bsonType": ["objectId", "string"] },
                      "skillName":      { "bsonType": ["string", "null"] },
                      "minProficiency": { "bsonType": ["int", "long"], "minimum": 1, "maximum": 5 },
                      "mandatory":      { "bsonType": "bool" }
                    }
                  }
                },
                "status":              { "enum": ["OPEN", "UPCOMING", "PLANNED", "CLOSED", "COMPLETED", "CANCELLED"] },
                "driveDate":           { "bsonType": "date" },
                "applicationDeadline": { "bsonType": "date" },
                "createdAt":           { "bsonType": "date" }
              }
            }
            """;

    public static final String APPLICATIONS_SCHEMA = """
            {
              "bsonType": "object",
              "title": "Student application with immutable embedded stageHistory[] trail",
              "required": ["studentId", "driveId", "currentStage", "appliedAt", "stageHistory"],
              "properties": {
                "studentId": { "bsonType": ["objectId", "string"], "description": "Reference -> students._id" },
                "driveId":   { "bsonType": ["objectId", "string"], "description": "Reference -> placement_drives._id" },
                "companyId": { "bsonType": ["objectId", "string", "null"], "description": "Reference -> companies._id" },
                "currentStage": { "enum": ["APPLICATION_SUBMITTED", "APPLICATION_REVIEWED", "SHORTLISTED", "ONLINE_ASSESSMENT", "TECHNICAL_INTERVIEW", "HR_INTERVIEW", "SELECTED", "OFFERED", "JOINED", "REJECTED", "WITHDRAWN"] },
                "appliedAt":     { "bsonType": "date" },
                "finalOfferLpa": { "bsonType": ["double", "int", "long", "decimal", "null"], "minimum": 0 },
                "studentSnapshot": { "bsonType": "object" },
                "stageHistory": {
                  "bsonType": "array",
                  "minItems": 1,
                  "items": {
                    "bsonType": "object",
                    "required": ["stage", "updatedBy", "timestamp"],
                    "properties": {
                      "stage":     { "enum": ["APPLICATION_SUBMITTED", "APPLICATION_REVIEWED", "SHORTLISTED", "ONLINE_ASSESSMENT", "TECHNICAL_INTERVIEW", "HR_INTERVIEW", "SELECTED", "OFFERED", "JOINED", "REJECTED", "WITHDRAWN"] },
                      "updatedBy": { "bsonType": "string" },
                      "timestamp": { "bsonType": "date" },
                      "remarks":   { "bsonType": ["string", "null"] }
                    }
                  }
                }
              }
            }
            """;

    /** Collection name -> $jsonSchema JSON (also displayed in the Live Schema Inspector). */
    public static Map<String, String> schemaDefinitions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("students", STUDENTS_SCHEMA);
        m.put("users", USERS_SCHEMA);
        m.put("placement_drives", DRIVES_SCHEMA);
        m.put("applications", APPLICATIONS_SCHEMA);
        return m;
    }

    // ------------------------------------------------------------------ apply

    /** Idempotent: safe to call on every launch and from both laptops against the same Atlas cluster. */
    public static void apply(MongoDatabase db) {
        List<String> existing = new ArrayList<>();
        db.listCollectionNames().into(existing);

        for (Map.Entry<String, String> e : schemaDefinitions().entrySet()) {
            applyValidator(db, existing, e.getKey(), Document.parse(e.getValue()));
        }
        for (String name : DatabaseConnection.REQUIRED_COLLECTIONS) {
            if (!existing.contains(name)) {
                try {
                    db.createCollection(name);
                    existing.add(name);
                } catch (MongoException ignored) {
                    // created concurrently by the other laptop - fine
                }
            }
        }
        createIndexes(db);
    }

    private static void applyValidator(MongoDatabase db, List<String> existing, String name, Document jsonSchema) {
        Document validator = new Document("$jsonSchema", jsonSchema);
        try {
            if (!existing.contains(name)) {
                db.createCollection(name, new CreateCollectionOptions().validationOptions(
                        new ValidationOptions()
                                .validator(validator)
                                .validationLevel(ValidationLevel.MODERATE)
                                .validationAction(ValidationAction.ERROR)));
                existing.add(name);
            } else {
                db.runCommand(new Document("collMod", name)
                        .append("validator", validator)
                        .append("validationLevel", "moderate")
                        .append("validationAction", "error"));
            }
        } catch (MongoCommandException ex) {
            System.err.println("[MongoDB] Could not apply $jsonSchema on `" + name + "`: " + ex.getErrorMessage());
        }
    }

    /** Reads back the validators currently stored on the server (listCollections -> options.validator). */
    public static Map<String, Document> currentValidators(MongoDatabase db) {
        Map<String, Document> out = new LinkedHashMap<>();
        for (Document info : db.listCollections()) {
            Document options = BsonUtil.sub(info, "options");
            Document validator = BsonUtil.sub(options, "validator");
            if (!validator.isEmpty()) {
                out.put(BsonUtil.str(info, "name"), validator);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- indexes

    public static void createIndexes(MongoDatabase db) {
        IndexOptions uniqueCi = new IndexOptions().unique(true).collation(BsonUtil.CASE_INSENSITIVE);
        IndexOptions ci = new IndexOptions().collation(BsonUtil.CASE_INSENSITIVE);

        MongoCollection<Document> users = db.getCollection("users");
        safeIndex(users, Indexes.ascending("email"), uniqueCi.name("uq_users_email_ci"));
        safeIndex(users, Indexes.ascending("username"), new IndexOptions().unique(true).collation(BsonUtil.CASE_INSENSITIVE).name("uq_users_username_ci"));
        safeIndex(users, Indexes.ascending("studentRollNumber"), new IndexOptions().sparse(true).collation(BsonUtil.CASE_INSENSITIVE).name("ix_users_rollNumber_ci"));
        safeIndex(users, Indexes.ascending("employeeId"), new IndexOptions().sparse(true).collation(BsonUtil.CASE_INSENSITIVE).name("ix_users_employeeId_ci"));
        safeIndex(users, Indexes.ascending("companyId"), new IndexOptions().name("ix_users_companyId"));
        safeIndex(users, Indexes.compoundIndex(Indexes.ascending("role"), Indexes.ascending("status")), new IndexOptions().name("ix_users_role_status"));

        MongoCollection<Document> students = db.getCollection("students");
        safeIndex(students, Indexes.ascending("rollNumber"), new IndexOptions().unique(true).collation(BsonUtil.CASE_INSENSITIVE).name("uq_students_rollNumber_ci"));
        safeIndex(students, Indexes.ascending("email"), new IndexOptions().unique(true).collation(BsonUtil.CASE_INSENSITIVE).name("uq_students_email_ci"));
        safeIndex(students, Indexes.ascending("userId"), new IndexOptions().name("ix_students_userId"));
        safeIndex(students, Indexes.compoundIndex(Indexes.ascending("branch"), Indexes.descending("academicMetrics.cgpa")), new IndexOptions().name("ix_students_branch_cgpa"));
        safeIndex(students, Indexes.compoundIndex(Indexes.ascending("approvalStatus"), Indexes.ascending("placementStatus")), new IndexOptions().name("ix_students_approval_placement"));
        safeIndex(students, Indexes.ascending("skills.skillId"), new IndexOptions().name("ix_students_skills_skillId_multikey"));
        safeIndex(students, Indexes.ascending("academicMetrics.graduationYear"), new IndexOptions().name("ix_students_gradYear"));

        MongoCollection<Document> faculty = db.getCollection("faculty");
        safeIndex(faculty, Indexes.ascending("userId"), new IndexOptions().name("ix_faculty_userId"));
        safeIndex(faculty, Indexes.ascending("email"), ci.name("ix_faculty_email_ci"));
        safeIndex(faculty, Indexes.ascending("facultyId"), new IndexOptions().sparse(true).collation(BsonUtil.CASE_INSENSITIVE).name("ix_faculty_facultyId_ci"));

        MongoCollection<Document> companies = db.getCollection("companies");
        safeIndex(companies, Indexes.ascending("name"), new IndexOptions().unique(true).collation(BsonUtil.CASE_INSENSITIVE).name("uq_companies_name_ci"));
        safeIndex(companies, Indexes.ascending("approvalStatus"), new IndexOptions().name("ix_companies_approvalStatus"));
        safeIndex(companies, Indexes.compoundIndex(Indexes.text("name"), Indexes.text("industry"), Indexes.text("description")),
                new IndexOptions().name("tx_companies_search").weights(new Document("name", 10).append("industry", 5).append("description", 1)));

        MongoCollection<Document> skills = db.getCollection("skills");
        safeIndex(skills, Indexes.ascending("name"), new IndexOptions().unique(true).collation(BsonUtil.CASE_INSENSITIVE).name("uq_skills_name_ci"));

        MongoCollection<Document> drives = db.getCollection("placement_drives");
        safeIndex(drives, Indexes.compoundIndex(Indexes.ascending("status"), Indexes.ascending("driveDate")), new IndexOptions().name("ix_drives_status_date"));
        safeIndex(drives, Indexes.ascending("companyId"), new IndexOptions().name("ix_drives_companyId"));
        safeIndex(drives, Indexes.ascending("eligibilityCriteria.allowedBranches"), new IndexOptions().name("ix_drives_allowedBranches_multikey"));
        safeIndex(drives, Indexes.ascending("requiredSkills.skillId"), new IndexOptions().name("ix_drives_requiredSkills_multikey"));
        safeIndex(drives, Indexes.compoundIndex(Indexes.text("jobRole"), Indexes.text("companyName"), Indexes.text("jobDescription")),
                new IndexOptions().name("tx_drives_search").weights(new Document("jobRole", 10).append("companyName", 8).append("jobDescription", 1)));

        MongoCollection<Document> applications = db.getCollection("applications");
        safeIndex(applications, Indexes.compoundIndex(Indexes.ascending("studentId"), Indexes.ascending("driveId")), new IndexOptions().unique(true).name("uq_applications_student_drive"));
        safeIndex(applications, Indexes.compoundIndex(Indexes.ascending("driveId"), Indexes.ascending("currentStage")), new IndexOptions().name("ix_applications_drive_stage"));
        safeIndex(applications, Indexes.ascending("companyId"), new IndexOptions().name("ix_applications_companyId"));
        safeIndex(applications, Indexes.descending("appliedAt"), new IndexOptions().name("ix_applications_appliedAt"));
        safeIndex(applications, Indexes.ascending("studentSnapshot.rollNumber"), new IndexOptions().name("ix_applications_rollNumber"));

        MongoCollection<Document> approvals = db.getCollection("approvals");
        safeIndex(approvals, Indexes.descending("decidedAt"), new IndexOptions().name("ix_approvals_decidedAt"));
        safeIndex(approvals, Indexes.compoundIndex(Indexes.ascending("requestType"), Indexes.ascending("department")), new IndexOptions().name("ix_approvals_type_dept"));

        MongoCollection<Document> adminRequests = db.getCollection("admin_requests");
        safeIndex(adminRequests, Indexes.compoundIndex(Indexes.ascending("status"), Indexes.descending("createdAt")), new IndexOptions().name("ix_adminRequests_status_createdAt"));

        MongoCollection<Document> announcements = db.getCollection("announcements");
        safeIndex(announcements, Indexes.descending("publishedAt"), new IndexOptions().name("ix_announcements_publishedAt"));
        safeIndex(announcements, Indexes.ascending("category"), new IndexOptions().name("ix_announcements_category"));

        MongoCollection<Document> auditLogs = db.getCollection("audit_logs");
        safeIndex(auditLogs, Indexes.descending("createdAt"), new IndexOptions().name("ix_audit_createdAt"));
        safeIndex(auditLogs, Indexes.compoundIndex(Indexes.ascending("actorId"), Indexes.descending("createdAt")), new IndexOptions().name("ix_audit_actor_createdAt"));
        safeIndex(auditLogs, Indexes.ascending("action"), new IndexOptions().name("ix_audit_action"));
    }

    private static void safeIndex(MongoCollection<Document> col, org.bson.conversions.Bson keys, IndexOptions options) {
        try {
            col.createIndex(keys, options);
        } catch (MongoException ex) {
            System.err.println("[MongoDB] Index " + options.getName() + " on " + col.getNamespace().getCollectionName()
                    + " not created: " + ex.getMessage());
        }
    }
}
