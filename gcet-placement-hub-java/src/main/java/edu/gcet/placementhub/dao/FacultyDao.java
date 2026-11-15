package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import edu.gcet.placementhub.models.Faculty;
import edu.gcet.placementhub.utils.DeptNormalizer;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the referenced {@code faculty} collection
 * ({@code userId} -> {@code users._id}; {@code authorizedDepartments[]} embedded).
 *
 * <pre>
 * { _id, userId: ObjectId, facultyId, employeeId, fullName, department, designation, email, phone,
 *   authorizedDepartments: [], facultyApproved, adminApproved, status, approvedByName, rejectionReason, createdAt }
 * </pre>
 */
public class FacultyDao {

    public static final String COLLECTION = "faculty";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    /** Merges FACULTY users with their faculty profile (two queries + in-memory join by userId / email). */
    public List<Faculty> findAll(MongoDatabase db) {
        List<Faculty> result = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        Map<String, Document> profilesByUser = new HashMap<>();
        Map<String, Document> profilesByEmail = new HashMap<>();
        List<Document> allProfiles = new ArrayList<>();
        for (Document f : col(db).find().sort(Sorts.descending("createdAt"))) {
            allProfiles.add(f);
            String uid = ref(f, "userId");
            if (uid != null) profilesByUser.put(uid, f);
            String em = lower(str(f, "email"));
            if (!em.isEmpty()) profilesByEmail.put(em, f);
        }

        for (Document u : db.getCollection(UserDao.COLLECTION).find(Filters.eq("role", "FACULTY")).sort(Sorts.descending("createdAt"))) {
            String uid = id(u);
            String username = str(u, "username");
            Document f = profilesByUser.get(uid);
            if (f == null) f = profilesByEmail.get(lower(str(u, "email")));

            Faculty fac = new Faculty();
            fac.setId(f != null ? id(f) : uid);
            fac.setUserId(uid);
            fac.setUsername(username);

            String empId = f != null ? str(f, "employeeId") : null;
            if (empId == null || empId.isBlank()) empId = f != null ? str(f, "facultyId") : null;
            if (empId == null || empId.isBlank()) empId = str(u, "employeeId");
            if (empId == null || empId.isBlank()) empId = username != null ? username.toUpperCase(Locale.ROOT) : "-";
            fac.setEmployeeId(empId);

            fac.setFullName(str(u, "fullName"));
            fac.setDepartment(str(u, "department"));
            String desig = f != null ? str(f, "designation") : str(u, "designation");
            fac.setDesignation(desig != null && !desig.isBlank() ? desig : "Faculty Coordinator");
            fac.setEmail(str(u, "email"));
            fac.setPhone(f != null ? str(f, "phone") : str(u, "phone"));

            List<String> authDepts = strList(u, "authorizedDepartments");
            if (authDepts.isEmpty() && f != null) authDepts = strList(f, "authorizedDepartments");
            if (authDepts.isEmpty()) authDepts = new ArrayList<>(DeptNormalizer.getFacultyAuthorizedDepts(username));
            if (authDepts.isEmpty() && fac.getDepartment() != null && !fac.getDepartment().isBlank()) {
                authDepts = List.of(fac.getDepartment());
            }
            fac.setAuthorizedDepartments(authDepts);

            String uStatus = str(u, "status");
            String fStatus = f != null ? str(f, "status") : null;
            String effStatus = uStatus != null ? uStatus : (fStatus != null ? fStatus : "APPROVED");
            if ("PENDING_APPROVAL".equalsIgnoreCase(effStatus)) effStatus = "PENDING";
            fac.setStatus(effStatus);
            fac.setActive(bool(u, "isActive", true));
            fac.setFacultyApproved(f == null || !f.containsKey("facultyApproved") || bool(f, "facultyApproved", true));
            fac.setAdminApproved(f == null || !f.containsKey("adminApproved")
                    ? !"PENDING".equalsIgnoreCase(effStatus)
                    : bool(f, "adminApproved", false));

            String appBy = f != null ? str(f, "approvedByName") : null;
            if (appBy == null || appBy.isBlank()) appBy = str(u, "approvedByName");
            if (appBy == null || appBy.isBlank()) appBy = ref(u, "approvedBy");
            fac.setApprovedByName(appBy);

            String rej = f != null ? str(f, "rejectionReason") : null;
            if (rej == null || rej.isBlank()) rej = str(u, "rejectionReason");
            fac.setRejectionReason(rej);
            fac.setCreatedAt(iso(u, "createdAt"));

            result.add(fac);
            if (fac.getEmail() != null) seenKeys.add(lower(fac.getEmail()));
            seenKeys.add(uid);
            if (f != null) seenKeys.add(id(f));
        }

        // Standalone faculty profiles without a users account
        for (Document f : allProfiles) {
            String email = lower(str(f, "email"));
            String uid = ref(f, "userId");
            if ((!email.isEmpty() && seenKeys.contains(email)) || (uid != null && seenKeys.contains(uid)) || seenKeys.contains(id(f))) {
                continue;
            }
            result.add(mapFaculty(f));
        }
        return result;
    }

    public Faculty findByUserId(MongoDatabase db, String userId) {
        Document d = col(db).find(refFilter("userId", userId)).first();
        return d == null ? null : mapFaculty(d);
    }

    public String insertFaculty(MongoDatabase db, Faculty f) {
        Document d = toDocument(f);
        col(db).insertOne(d);
        f.setId(id(d));
        return f.getId();
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    public static Faculty mapFaculty(Document d) {
        Faculty f = new Faculty();
        f.setId(id(d));
        f.setUserId(ref(d, "userId"));
        String facId = str(d, "facultyId");
        f.setUsername(facId);
        String empId = str(d, "employeeId");
        f.setEmployeeId(empId != null && !empId.isBlank() ? empId : facId);
        f.setFullName(str(d, "fullName"));
        f.setDepartment(str(d, "department"));
        f.setDesignation(str(d, "designation", "Faculty Coordinator"));
        f.setEmail(str(d, "email"));
        f.setPhone(str(d, "phone"));
        List<String> authDepts = strList(d, "authorizedDepartments");
        if (authDepts.isEmpty() && f.getDepartment() != null && !f.getDepartment().isBlank()) {
            authDepts = List.of(f.getDepartment());
        }
        f.setAuthorizedDepartments(authDepts);
        f.setFacultyApproved(bool(d, "facultyApproved", true));
        f.setAdminApproved(bool(d, "adminApproved", false));
        f.setApprovedByName(str(d, "approvedByName"));
        f.setRejectionReason(str(d, "rejectionReason"));
        f.setStatus(str(d, "status", "ACTIVE"));
        f.setActive("ACTIVE".equalsIgnoreCase(f.getStatus()) || "APPROVED".equalsIgnoreCase(f.getStatus()));
        f.setCreatedAt(iso(d, "createdAt"));
        return f;
    }

    public static Document toDocument(Faculty f) {
        Document d = new Document();
        if (f.getId() != null && !f.getId().isBlank()) d.append("_id", toKey(f.getId()));
        return d.append("userId", toKey(f.getUserId()))
                .append("facultyId", f.getEmployeeId())
                .append("employeeId", f.getEmployeeId())
                .append("fullName", f.getFullName())
                .append("department", f.getDepartment())
                .append("designation", nvl(f.getDesignation(), "Faculty Coordinator"))
                .append("email", f.getEmail())
                .append("phone", nvl(f.getPhone(), ""))
                .append("authorizedDepartments", new ArrayList<>(f.getAuthorizedDepartments()))
                .append("facultyApproved", f.isFacultyApproved())
                .append("adminApproved", f.isAdminApproved())
                .append("status", nvl(f.getStatus(), "PENDING_APPROVAL"))
                .append("approvedByName", f.getApprovedByName())
                .append("rejectionReason", f.getRejectionReason())
                .append("createdAt", f.getCreatedAt() != null ? toDateOrToday(f.getCreatedAt()) : now());
    }
}
