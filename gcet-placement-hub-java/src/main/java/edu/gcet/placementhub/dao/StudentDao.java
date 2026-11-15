package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.StudentCertification;
import edu.gcet.placementhub.models.StudentInternship;
import edu.gcet.placementhub.models.StudentProject;
import edu.gcet.placementhub.models.StudentSkill;
import edu.gcet.placementhub.models.UserRole;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the {@code students} collection - the flagship embedded document:
 *
 * <pre>
 * {
 *   _id: ObjectId, rollNumber, studentIdCard, fullName, email, phone, branch, section,
 *   userId: ObjectId (-> users),
 *   academicMetrics: { cgpa, tenthPercentage, interPercentage, backlogs, yearOfStudy, academicYear, graduationYear },
 *   skills:         [ { skillId, skillName, proficiency 1-5, verifiedByFaculty, verifiedByFacultyId, verifiedAt } ],
 *   projects:       [ { _id, title, description, technologies[], link, duration, createdAt } ],
 *   certifications: [ { _id, name, issuingOrg, issueDate, credentialId, createdAt } ],
 *   internships:    [ { _id, companyName, role, duration, description, createdAt } ],
 *   placementStatus, placedCompanyId, placedPackageLpa,
 *   approvalStatus, approvedByFacultyId, approvedByFacultyName, approvedAt, rejectionReason,
 *   resumeUrl, createdAt, updatedAt
 * }
 * </pre>
 * The four former 1-to-many child tables became arrays because they are always read together
 * with the student (1-to-few, tightly coupled, no independent lifecycle).
 */
public class StudentDao {

    public static final String COLLECTION = "students";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    public Student findByRollNumber(MongoDatabase db, String rollNumber) {
        if (rollNumber == null || rollNumber.isBlank()) return null;
        Document d = col(db).find(Filters.eq("rollNumber", rollNumber.trim())).collation(CASE_INSENSITIVE).first();
        return d == null ? null : mapStudent(d);
    }

    public Student findByUserId(MongoDatabase db, String userId) {
        if (userId == null || userId.isBlank()) return null;
        Document d = col(db).find(refFilter("userId", userId)).first();
        return d == null ? null : mapStudent(d);
    }

    public Student findByEmail(MongoDatabase db, String email) {
        if (email == null || email.isBlank()) return null;
        Document d = col(db).find(Filters.eq("email", email.trim())).collation(CASE_INSENSITIVE).first();
        return d == null ? null : mapStudent(d);
    }

    public String findNameByRollNumber(MongoDatabase db, String rollNumber) {
        Document d = col(db).find(Filters.eq("rollNumber", nvl(rollNumber, "")))
                .collation(CASE_INSENSITIVE).projection(new Document("fullName", 1)).first();
        return d == null ? null : str(d, "fullName");
    }

    public Student findByIdOrRoll(MongoDatabase db, String idOrRoll) {
        if (idOrRoll == null || idOrRoll.isBlank()) return null;
        String clean = idOrRoll.trim();
        Document d = col(db).find(Filters.or(idFilter(clean), Filters.eq("rollNumber", clean)))
                .collation(CASE_INSENSITIVE).first();
        return d == null ? null : mapStudent(d);
    }

    public boolean existsByRollOrEmail(MongoDatabase db, String rollNumber, String email) {
        return col(db).find(Filters.or(
                        Filters.eq("rollNumber", nvl(rollNumber, "")),
                        Filters.eq("email", nvl(email, ""))))
                .collation(CASE_INSENSITIVE).projection(new Document("_id", 1)).first() != null;
    }

    /** One query returns every student together with all embedded sub-documents (no joins). */
    public List<Student> findAll(MongoDatabase db) {
        List<Student> students = new ArrayList<>();
        for (Document d : col(db).find().sort(Sorts.descending("createdAt"))) {
            students.add(mapStudent(d));
        }
        return students;
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    /** Inserts the student document and its login account (unless the username is already taken). */
    public void insertStudentWithUser(MongoDatabase db, Student s, String passwordHash, String salt) {
        if (s.getApprovalStatus() == null || s.getApprovalStatus().isBlank()) s.setApprovalStatus("APPROVED");
        if (s.getPlacementStatus() == null || s.getPlacementStatus().isBlank()) s.setPlacementStatus("UNPLACED");
        if (s.getAcademicYear() <= 0) s.setAcademicYear(4);
        if (s.getYearOfStudy() <= 0) s.setYearOfStudy(4);
        if (s.getGraduationYear() <= 0) s.setGraduationYear(2026);

        ObjectId userKey = s.getUserId() != null && ObjectId.isValid(s.getUserId()) ? new ObjectId(s.getUserId()) : new ObjectId();
        s.setUserId(userKey.toHexString());

        Document studentDoc = toDocument(s);
        col(db).insertOne(studentDoc);
        s.setId(id(studentDoc));

        UserDao userDao = new UserDao();
        if (!userDao.existsByUsername(db, s.getRollNumber())) {
            edu.gcet.placementhub.models.User u = new edu.gcet.placementhub.models.User();
            u.setId(userKey.toHexString());
            u.setUsername(s.getRollNumber());
            u.setEmail(s.getEmail());
            u.setFullName(s.getFullName());
            u.setRole(UserRole.STUDENT);
            u.setDepartment(s.getBranch());
            u.setStudentRollNumber(s.getRollNumber());
            u.setStatus("APPROVED".equalsIgnoreCase(s.getApprovalStatus()) ? "APPROVED" : s.getApprovalStatus());
            u.setActive("APPROVED".equalsIgnoreCase(s.getApprovalStatus()));
            userDao.insertUser(db, u, passwordHash, salt);
        }
    }

    public void updateStudent(MongoDatabase db, Student updated, boolean selfStudentOnly) {
        List<Bson> sets = new ArrayList<>();
        sets.add(Updates.set("phone", updated.getPhone() != null ? updated.getPhone().trim() : ""));
        sets.add(Updates.set("resumeUrl", nvl(updated.getResumeUrl(), null)));
        if (!selfStudentOnly) {
            sets.add(Updates.set("fullName", updated.getFullName()));
            sets.add(Updates.set("email", updated.getEmail()));
            sets.add(Updates.set("branch", updated.getBranch()));
            sets.add(Updates.set("academicMetrics.cgpa", updated.getCgpa()));
            sets.add(Updates.set("academicMetrics.tenthPercentage", updated.getTenthPercentage()));
            sets.add(Updates.set("academicMetrics.interPercentage", updated.getInterPercentage()));
            sets.add(Updates.set("academicMetrics.graduationYear", updated.getGraduationYear()));
            sets.add(Updates.set("academicMetrics.backlogs", updated.getBacklogs()));
            sets.add(Updates.set("placementStatus", nvl(updated.getPlacementStatus(), "UNPLACED")));
        }
        sets.add(Updates.set("updatedAt", now()));
        col(db).updateOne(idFilter(updated.getId()), Updates.combine(sets));
    }

    public int countApplicationsForStudent(MongoDatabase db, String studentId) {
        return (int) db.getCollection(ApplicationDao.COLLECTION).countDocuments(refFilter("studentId", studentId));
    }

    /** Deleting the document removes every embedded sub-document atomically; the login account follows. */
    public void deleteStudentCascade(MongoDatabase db, String studentId, String rollNumber) {
        col(db).deleteOne(idFilter(studentId));
        if (rollNumber != null && !rollNumber.isBlank()) {
            db.getCollection(UserDao.COLLECTION).deleteMany(
                    Filters.and(Filters.eq("studentRollNumber", rollNumber.trim()), Filters.eq("role", "STUDENT")));
        }
    }

    // --------------------------------------------------- embedded skills array

    public void upsertStudentSkill(MongoDatabase db, String studentId, String skillId, int proficiency) {
        upsertStudentSkill(db, studentId, skillId, proficiency, false, null);
    }

    public void upsertStudentSkill(
            MongoDatabase db,
            String studentId,
            String skillId,
            int proficiency,
            boolean canVerify,
            String verifierUserId
    ) {
        int clamped = Math.min(5, Math.max(1, proficiency));
        Object skillKey = toKey(skillId);
        String skillName = lookupSkillName(db, skillId);

        Bson existingFilter = Filters.and(idFilter(studentId), Filters.eq("skills.skillId", skillKey));
        List<Bson> sets = new ArrayList<>();
        sets.add(Updates.set("skills.$.proficiency", clamped));
        if (skillName != null) sets.add(Updates.set("skills.$.skillName", skillName));
        if (canVerify && verifierUserId != null) {
            sets.add(Updates.set("skills.$.verifiedByFaculty", true));
            sets.add(Updates.set("skills.$.verifiedByFacultyId", toKey(verifierUserId)));
            sets.add(Updates.set("skills.$.verifiedAt", now()));
        }
        sets.add(Updates.set("updatedAt", now()));
        long matched = col(db).updateOne(existingFilter, Updates.combine(sets)).getMatchedCount();

        if (matched == 0) {
            Document entry = new Document("skillId", skillKey)
                    .append("skillName", skillName)
                    .append("proficiency", clamped)
                    .append("verifiedByFaculty", canVerify && verifierUserId != null)
                    .append("verifiedByFacultyId", canVerify && verifierUserId != null ? toKey(verifierUserId) : null)
                    .append("verifiedAt", canVerify && verifierUserId != null ? now() : null);
            col(db).updateOne(idFilter(studentId), Updates.combine(
                    Updates.push("skills", entry), Updates.set("updatedAt", now())));
        }
    }

    public void verifyStudentSkill(MongoDatabase db, String studentId, String skillId, String verifierUserId) {
        col(db).updateOne(
                Filters.and(idFilter(studentId), Filters.eq("skills.skillId", toKey(skillId))),
                Updates.combine(
                        Updates.set("skills.$.verifiedByFaculty", true),
                        Updates.set("skills.$.verifiedByFacultyId", toKey(verifierUserId)),
                        Updates.set("skills.$.verifiedAt", now()),
                        Updates.set("updatedAt", now())));
    }

    private String lookupSkillName(MongoDatabase db, String skillId) {
        Document sk = db.getCollection(SkillDao.COLLECTION).find(idFilter(skillId)).projection(new Document("name", 1)).first();
        return sk == null ? null : str(sk, "name");
    }

    // -------------------------------------------- embedded portfolio arrays

    public void insertProject(MongoDatabase db, StudentProject project) {
        if (project.getId() == null || !ObjectId.isValid(project.getId())) project.setId(newId());
        project.setCreatedAt(java.time.Instant.now().toString());
        col(db).updateOne(idFilter(project.getStudentId()), Updates.combine(
                Updates.push("projects", projectDoc(project)), Updates.set("updatedAt", now())));
    }

    public void deleteProject(MongoDatabase db, String projectId, String studentId) {
        col(db).updateOne(idFilter(studentId), Updates.pull("projects", new Document("_id", toKey(projectId))));
    }

    public void insertCertification(MongoDatabase db, StudentCertification cert) {
        if (cert.getId() == null || !ObjectId.isValid(cert.getId())) cert.setId(newId());
        cert.setCreatedAt(java.time.Instant.now().toString());
        col(db).updateOne(idFilter(cert.getStudentId()), Updates.combine(
                Updates.push("certifications", certificationDoc(cert)), Updates.set("updatedAt", now())));
    }

    public void deleteCertification(MongoDatabase db, String certId, String studentId) {
        col(db).updateOne(idFilter(studentId), Updates.pull("certifications", new Document("_id", toKey(certId))));
    }

    public void insertInternship(MongoDatabase db, StudentInternship internship) {
        if (internship.getId() == null || !ObjectId.isValid(internship.getId())) internship.setId(newId());
        internship.setCreatedAt(java.time.Instant.now().toString());
        col(db).updateOne(idFilter(internship.getStudentId()), Updates.combine(
                Updates.push("internships", internshipDoc(internship)), Updates.set("updatedAt", now())));
    }

    public void deleteInternship(MongoDatabase db, String internshipId, String studentId) {
        col(db).updateOne(idFilter(studentId), Updates.pull("internships", new Document("_id", toKey(internshipId))));
    }

    public List<StudentSkill> findSkillsForStudent(MongoDatabase db, String studentId) {
        Student s = findByIdOrRoll(db, studentId);
        return s == null ? new ArrayList<>() : s.getSkills();
    }

    public List<StudentProject> findProjectsForStudent(MongoDatabase db, String studentId) {
        Student s = findByIdOrRoll(db, studentId);
        return s == null ? new ArrayList<>() : s.getProjects();
    }

    public List<StudentCertification> findCertificationsForStudent(MongoDatabase db, String studentId) {
        Student s = findByIdOrRoll(db, studentId);
        return s == null ? new ArrayList<>() : s.getCertifications();
    }

    public List<StudentInternship> findInternshipsForStudent(MongoDatabase db, String studentId) {
        Student s = findByIdOrRoll(db, studentId);
        return s == null ? new ArrayList<>() : s.getInternships();
    }

    // ------------------------------------------------------------------ mapping

    public static Student mapStudent(Document d) {
        Student s = new Student();
        s.setId(id(d));
        s.setRollNumber(str(d, "rollNumber"));
        s.setStudentIdCard(str(d, "studentIdCard"));
        s.setFullName(str(d, "fullName"));
        s.setEmail(str(d, "email"));
        s.setPhone(str(d, "phone"));
        s.setBranch(str(d, "branch"));
        s.setSection(str(d, "section"));
        Document am = sub(d, "academicMetrics");
        s.setYearOfStudy(intVal(am, "yearOfStudy", 4));
        s.setAcademicYear(intVal(am, "academicYear", s.getYearOfStudy()));
        s.setCgpa(dbl(am, "cgpa", 0));
        s.setTenthPercentage(dbl(am, "tenthPercentage", 0));
        s.setInterPercentage(dbl(am, "interPercentage", 0));
        s.setGraduationYear(intVal(am, "graduationYear", 2026));
        s.setBacklogs(intVal(am, "backlogs", 0));
        s.setPlacementStatus(str(d, "placementStatus", "UNPLACED"));
        s.setApprovalStatus(str(d, "approvalStatus"));
        s.setApprovedByFacultyId(ref(d, "approvedByFacultyId"));
        s.setApprovedByFacultyName(str(d, "approvedByFacultyName"));
        s.setRejectionReason(str(d, "rejectionReason"));
        s.setUserId(ref(d, "userId"));
        s.setPlacedCompanyId(ref(d, "placedCompanyId"));
        s.setPlacedPackageLpa(dblOrNull(d, "placedPackageLpa"));
        s.setResumeUrl(str(d, "resumeUrl"));
        s.setApprovedAt(iso(d, "approvedAt"));
        s.setCreatedAt(iso(d, "createdAt"));

        List<StudentSkill> skills = new ArrayList<>();
        for (Document sk : docList(d, "skills")) {
            StudentSkill ss = new StudentSkill();
            ss.setStudentId(s.getId());
            ss.setSkillId(ref(sk, "skillId"));
            ss.setSkillName(str(sk, "skillName"));
            ss.setProficiency(intVal(sk, "proficiency", 3));
            ss.setVerifiedByFaculty(bool(sk, "verifiedByFaculty", false));
            ss.setVerifiedByFacultyId(ref(sk, "verifiedByFacultyId"));
            ss.setVerifiedAt(iso(sk, "verifiedAt"));
            skills.add(ss);
        }
        s.setSkills(skills);

        List<StudentProject> projects = new ArrayList<>();
        for (Document p : docList(d, "projects")) {
            StudentProject sp = new StudentProject();
            sp.setId(id(p));
            sp.setStudentId(s.getId());
            sp.setTitle(str(p, "title"));
            sp.setDescription(str(p, "description"));
            sp.setTechnologies(strList(p, "technologies"));
            sp.setLink(str(p, "link"));
            sp.setDuration(str(p, "duration"));
            sp.setCreatedAt(iso(p, "createdAt"));
            projects.add(sp);
        }
        s.setProjects(projects);

        List<StudentCertification> certs = new ArrayList<>();
        for (Document c : docList(d, "certifications")) {
            StudentCertification sc = new StudentCertification();
            sc.setId(id(c));
            sc.setStudentId(s.getId());
            sc.setName(str(c, "name"));
            sc.setIssuingOrg(str(c, "issuingOrg"));
            sc.setIssueDate(str(c, "issueDate"));
            sc.setCredentialId(str(c, "credentialId"));
            sc.setCreatedAt(iso(c, "createdAt"));
            certs.add(sc);
        }
        s.setCertifications(certs);

        List<StudentInternship> interns = new ArrayList<>();
        for (Document i : docList(d, "internships")) {
            StudentInternship si = new StudentInternship();
            si.setId(id(i));
            si.setStudentId(s.getId());
            si.setCompanyName(str(i, "companyName"));
            si.setRole(str(i, "role"));
            si.setDuration(str(i, "duration"));
            si.setDescription(str(i, "description"));
            si.setCreatedAt(iso(i, "createdAt"));
            interns.add(si);
        }
        s.setInternships(interns);
        return s;
    }

    public static Document toDocument(Student s) {
        Document d = new Document();
        if (s.getId() != null && !s.getId().isBlank()) d.append("_id", toKey(s.getId()));
        d.append("rollNumber", s.getRollNumber())
                .append("studentIdCard", s.getStudentIdCard())
                .append("fullName", s.getFullName())
                .append("email", s.getEmail())
                .append("phone", nvl(s.getPhone(), ""))
                .append("branch", s.getBranch())
                .append("section", s.getSection())
                .append("userId", toKey(s.getUserId()))
                .append("academicMetrics", new Document("cgpa", s.getCgpa())
                        .append("tenthPercentage", s.getTenthPercentage())
                        .append("interPercentage", s.getInterPercentage())
                        .append("backlogs", s.getBacklogs())
                        .append("yearOfStudy", s.getYearOfStudy() > 0 ? s.getYearOfStudy() : 4)
                        .append("academicYear", s.getAcademicYear() > 0 ? s.getAcademicYear() : 4)
                        .append("graduationYear", s.getGraduationYear() > 0 ? s.getGraduationYear() : 2026))
                .append("placementStatus", nvl(s.getPlacementStatus(), "UNPLACED"))
                .append("placedCompanyId", toKey(s.getPlacedCompanyId()))
                .append("placedPackageLpa", s.getPlacedPackageLpa())
                .append("approvalStatus", nvl(s.getApprovalStatus(), "PENDING_APPROVAL"))
                .append("approvedByFacultyId", toKey(s.getApprovedByFacultyId()))
                .append("approvedByFacultyName", s.getApprovedByFacultyName())
                .append("approvedAt", toDate(s.getApprovedAt()))
                .append("rejectionReason", s.getRejectionReason())
                .append("resumeUrl", nvl(s.getResumeUrl(), null))
                .append("createdAt", s.getCreatedAt() != null ? toDateOrToday(s.getCreatedAt()) : now())
                .append("updatedAt", now());

        List<Document> skills = new ArrayList<>();
        for (StudentSkill ss : s.getSkills()) {
            skills.add(new Document("skillId", toKey(ss.getSkillId()))
                    .append("skillName", ss.getSkillName())
                    .append("proficiency", Math.min(5, Math.max(1, ss.getProficiency())))
                    .append("verifiedByFaculty", ss.isVerifiedByFaculty())
                    .append("verifiedByFacultyId", toKey(ss.getVerifiedByFacultyId()))
                    .append("verifiedAt", toDate(ss.getVerifiedAt())));
        }
        d.append("skills", skills);

        List<Document> projects = new ArrayList<>();
        for (StudentProject p : s.getProjects()) projects.add(projectDoc(p));
        d.append("projects", projects);

        List<Document> certs = new ArrayList<>();
        for (StudentCertification c : s.getCertifications()) certs.add(certificationDoc(c));
        d.append("certifications", certs);

        List<Document> interns = new ArrayList<>();
        for (StudentInternship i : s.getInternships()) interns.add(internshipDoc(i));
        d.append("internships", interns);
        return d;
    }

    private static Document projectDoc(StudentProject p) {
        return new Document("_id", p.getId() != null && ObjectId.isValid(p.getId()) ? new ObjectId(p.getId()) : new ObjectId())
                .append("title", p.getTitle())
                .append("description", nvl(p.getDescription(), ""))
                .append("technologies", new ArrayList<>(p.getTechnologies()))
                .append("link", nvl(p.getLink(), null))
                .append("duration", nvl(p.getDuration(), null))
                .append("createdAt", p.getCreatedAt() != null ? toDateOrToday(p.getCreatedAt()) : now());
    }

    private static Document certificationDoc(StudentCertification c) {
        return new Document("_id", c.getId() != null && ObjectId.isValid(c.getId()) ? new ObjectId(c.getId()) : new ObjectId())
                .append("name", c.getName())
                .append("issuingOrg", c.getIssuingOrg())
                .append("issueDate", c.getIssueDate())
                .append("credentialId", nvl(c.getCredentialId(), null))
                .append("createdAt", c.getCreatedAt() != null ? toDateOrToday(c.getCreatedAt()) : now());
    }

    private static Document internshipDoc(StudentInternship i) {
        return new Document("_id", i.getId() != null && ObjectId.isValid(i.getId()) ? new ObjectId(i.getId()) : new ObjectId())
                .append("companyName", i.getCompanyName())
                .append("role", i.getRole())
                .append("duration", i.getDuration())
                .append("description", nvl(i.getDescription(), ""))
                .append("createdAt", i.getCreatedAt() != null ? toDateOrToday(i.getCreatedAt()) : now());
    }
}
