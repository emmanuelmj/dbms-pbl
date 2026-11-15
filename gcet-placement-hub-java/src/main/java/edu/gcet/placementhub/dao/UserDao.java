package edu.gcet.placementhub.dao;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.DatabaseException;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.DeptNormalizer;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for the referenced {@code users} collection.
 *
 * <pre>
 * {
 *   _id: ObjectId, username, email, fullName, role, department, designation, employeeId, phone,
 *   studentRollNumber, companyId: ObjectId, companyName, status, isActive, mustChangePassword,
 *   passwordHash, salt, authorizedDepartments: [], lastLogin, createdAt, approvedBy, approvedByName,
 *   approvedAt, rejectionReason
 * }
 * </pre>
 * Unique case-insensitive indexes exist on {@code email} and {@code username}.
 */
public class UserDao {

    public static final String COLLECTION = "users";

    public static MongoCollection<Document> col(MongoDatabase db) {
        return db.getCollection(COLLECTION);
    }

    public User findByLoginIdentifier(MongoDatabase db, String identifier) {
        String clean = identifier == null ? "" : identifier.trim();
        if (clean.isEmpty()) return null;

        Document doc = col(db).find(Filters.or(
                        Filters.eq("username", clean),
                        Filters.eq("studentRollNumber", clean),
                        Filters.eq("email", clean),
                        Filters.eq("employeeId", clean)))
                .collation(CASE_INSENSITIVE)
                .first();
        if (doc != null) return mapUser(doc);

        // Secondary fallback: Faculty ID / Employee ID stored on the faculty profile
        Document fac = db.getCollection(FacultyDao.COLLECTION).find(Filters.or(
                        Filters.eq("facultyId", clean),
                        Filters.eq("employeeId", clean)))
                .collation(CASE_INSENSITIVE)
                .first();
        if (fac != null && fac.get("userId") != null) {
            Document byId = col(db).find(Filters.eq("_id", fac.get("userId"))).first();
            if (byId != null) return mapUser(byId);
        }
        return null;
    }

    public boolean existsByUsername(MongoDatabase db, String username) {
        return col(db).find(Filters.eq("username", nvl(username, "")))
                .collation(CASE_INSENSITIVE).projection(new Document("_id", 1)).first() != null;
    }

    public String findUsernameByEmail(MongoDatabase db, String email) {
        Document d = col(db).find(Filters.eq("email", nvl(email, "")))
                .collation(CASE_INSENSITIVE).projection(new Document("username", 1)).first();
        return d == null ? null : str(d, "username");
    }

    public boolean existsByEmailOrUsername(MongoDatabase db, String email, String username) {
        return col(db).find(Filters.or(
                        Filters.eq("email", nvl(email, "")),
                        Filters.eq("username", nvl(username, ""))))
                .collation(CASE_INSENSITIVE).projection(new Document("_id", 1)).first() != null;
    }

    public void updateLastLogin(MongoDatabase db, String userId) {
        col(db).updateOne(idFilter(userId), Updates.set("lastLogin", now()));
    }

    public void activateApprovedStudentUser(MongoDatabase db, String userId) {
        col(db).updateOne(idFilter(userId), Updates.combine(
                Updates.set("isActive", true),
                Updates.set("status", "APPROVED")));
    }

    public void updatePasswordHash(MongoDatabase db, String userId, String passwordHash, String salt) {
        col(db).updateOne(idFilter(userId), Updates.combine(
                Updates.set("passwordHash", passwordHash),
                Updates.set("salt", salt),
                Updates.set("mustChangePassword", false),
                Updates.set("updatedAt", now())));
    }

    public User findById(MongoDatabase db, String userId) {
        if (userId == null || userId.isBlank()) return null;
        Document d = col(db).find(idFilter(userId.trim())).first();
        return d == null ? null : mapUser(d);
    }

    public boolean existsByEmailExcludingUser(MongoDatabase db, String email, String excludeUserId) {
        Bson filter = Filters.and(
                Filters.eq("email", nvl(email, "")),
                Filters.ne("_id", toKey(excludeUserId != null ? excludeUserId : "")));
        return col(db).find(filter).collation(CASE_INSENSITIVE).projection(new Document("_id", 1)).first() != null;
    }

    public void updateUserProfile(
            MongoDatabase db,
            String userId,
            String fullName,
            String email,
            String phone,
            String department,
            String designation
    ) {
        col(db).updateOne(idFilter(userId), Updates.combine(
                Updates.set("fullName", fullName),
                Updates.set("email", email),
                Updates.set("phone", phone != null ? phone : ""),
                Updates.set("department", department != null ? department : ""),
                Updates.set("designation", designation != null ? designation : ""),
                Updates.set("updatedAt", now())));
    }

    /** Inserts an ACTIVE account (admin-provisioned). Returns the generated hex id. */
    public String createUserAccount(MongoDatabase db, User u, String passwordHash, String salt) {
        if (u.getStatus() == null) u.setStatus("ACTIVE");
        u.setActive(true);
        Document doc = toDocument(u, passwordHash, salt);
        col(db).insertOne(doc);
        u.setId(id(doc));
        return u.getId();
    }

    /** Generic insert honouring whatever status / flags are already set on the model. */
    public String insertUser(MongoDatabase db, User u, String passwordHash, String salt) {
        Document doc = toDocument(u, passwordHash, salt);
        col(db).insertOne(doc);
        u.setId(id(doc));
        return u.getId();
    }

    public int countActiveAdmins(MongoDatabase db) {
        return (int) col(db).countDocuments(Filters.and(
                Filters.eq("role", UserRole.ADMIN.name()),
                Filters.or(Filters.exists("isActive", false), Filters.eq("isActive", true))));
    }

    public void deleteUserById(MongoDatabase db, String userId) {
        col(db).deleteOne(idFilter(userId));
    }

    public List<User> findAll() throws DatabaseException {
        return findAll(DatabaseConnection.getInstance().getDatabase());
    }

    public List<User> findAll(MongoDatabase db) {
        List<User> list = new ArrayList<>();
        FindIterable<Document> it = col(db).find().sort(Sorts.descending("createdAt"));
        for (Document d : it) {
            User u = mapUser(d);
            u.setPasswordHash(null);
            u.setSalt(null);
            list.add(u);
        }
        return list;
    }

    public long count(MongoDatabase db) {
        return col(db).countDocuments();
    }

    // ------------------------------------------------------------------ mapping

    public static User mapUser(Document d) {
        User u = new User();
        u.setId(id(d));
        u.setUsername(str(d, "username"));
        u.setEmail(str(d, "email"));
        u.setFullName(str(d, "fullName"));
        u.setRole(UserRole.fromString(str(d, "role")));
        u.setDepartment(str(d, "department"));
        u.setDesignation(str(d, "designation"));
        u.setEmployeeId(str(d, "employeeId"));
        u.setPhone(str(d, "phone"));
        u.setStudentRollNumber(str(d, "studentRollNumber"));
        u.setCompanyId(ref(d, "companyId"));
        u.setCompanyName(str(d, "companyName"));
        u.setStatus(str(d, "status"));
        u.setMustChangePassword(bool(d, "mustChangePassword", false));
        u.setActive(bool(d, "isActive", true));
        u.setPasswordHash(str(d, "passwordHash"));
        u.setSalt(str(d, "salt"));
        u.setApprovedBy(ref(d, "approvedBy"));
        u.setApprovedByName(str(d, "approvedByName"));
        u.setRejectionReason(str(d, "rejectionReason"));
        u.setLastLogin(iso(d, "lastLogin"));
        u.setCreatedAt(iso(d, "createdAt"));
        u.setApprovedAt(iso(d, "approvedAt"));

        List<String> depts = strList(d, "authorizedDepartments");
        if (depts.isEmpty()) {
            depts = new ArrayList<>(DeptNormalizer.getFacultyAuthorizedDepts(u.getUsername()));
        }
        u.setAuthorizedDepartments(depts);
        return u;
    }

    public static Document toDocument(User u, String passwordHash, String salt) {
        Document d = new Document();
        if (u.getId() != null && !u.getId().isBlank()) d.append("_id", toKey(u.getId()));
        d.append("username", u.getUsername())
                .append("email", u.getEmail())
                .append("fullName", u.getFullName())
                .append("role", (u.getRole() != null ? u.getRole() : UserRole.STUDENT).name())
                .append("department", nvl(u.getDepartment(), ""))
                .append("designation", nvl(u.getDesignation(), ""))
                .append("employeeId", u.getEmployeeId())
                .append("phone", nvl(u.getPhone(), ""))
                .append("studentRollNumber", u.getStudentRollNumber())
                .append("companyId", toKey(u.getCompanyId()))
                .append("companyName", u.getCompanyName())
                .append("status", nvl(u.getStatus(), "ACTIVE"))
                .append("isActive", u.isActive())
                .append("mustChangePassword", u.isMustChangePassword())
                .append("passwordHash", passwordHash)
                .append("salt", salt)
                .append("authorizedDepartments", new ArrayList<>(u.getAuthorizedDepartments()))
                .append("approvedBy", toKey(u.getApprovedBy()))
                .append("approvedByName", u.getApprovedByName())
                .append("rejectionReason", u.getRejectionReason())
                .append("lastLogin", toDate(u.getLastLogin()))
                .append("approvedAt", toDate(u.getApprovedAt()))
                .append("createdAt", u.getCreatedAt() != null ? toDateOrToday(u.getCreatedAt()) : now());
        return d;
    }
}
