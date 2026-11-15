package edu.gcet.placementhub.database;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.dao.AnnouncementDao;
import edu.gcet.placementhub.dao.ApplicationDao;
import edu.gcet.placementhub.dao.ApprovalDao;
import edu.gcet.placementhub.dao.AuditLogDao;
import edu.gcet.placementhub.dao.CompanyDao;
import edu.gcet.placementhub.dao.DriveDao;
import edu.gcet.placementhub.dao.FacultyDao;
import edu.gcet.placementhub.dao.SkillDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.models.ApplicationStage;
import edu.gcet.placementhub.models.Company;
import edu.gcet.placementhub.models.DriveRequiredSkill;
import edu.gcet.placementhub.models.Faculty;
import edu.gcet.placementhub.models.GctcAnnouncement;
import edu.gcet.placementhub.models.PlacementDrive;
import edu.gcet.placementhub.models.Skill;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.StudentCertification;
import edu.gcet.placementhub.models.StudentInternship;
import edu.gcet.placementhub.models.StudentProject;
import edu.gcet.placementhub.models.StudentSkill;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.PasswordHasher;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Idempotent demo-data seeder for the DBMS presentation.
 *
 * Runs on first launch and populates realistic Geethanjali College of Engineering &amp; Technology
 * (Cheeryal, Hyderabad) students, faculty coordinators, leadership accounts, recruiters, companies,
 * placement drives, applications with stage trails, approvals, announcements and audit logs.
 *
 * Every collection is seeded only when it is empty, so running it once from one laptop populates
 * the shared Atlas cluster for both laptops, and re-running it later is a no-op. Unique indexes
 * ({@link MongoSchemaInitializer}) guarantee that a race between two laptops can never duplicate data.
 *
 * Demo passwords come from {@code GCET_BOOTSTRAP_*_PASS} in .env, falling back to
 * {@code GCET_DEMO_PASSWORD} (default {@code Gcet@2025}).
 */
public final class MongoSeedData {

    private static final Random RNG = new Random(2025);
    private static final Map<String, String[]> HASH_CACHE = new HashMap<>();

    private MongoSeedData() {
    }

    /** Entry point used by {@link DatabaseConnection} on first connection. Returns a short summary. */
    public static synchronized String seedIfEmpty(MongoDatabase db) {
        StringBuilder summary = new StringBuilder();
        try {
            SeedContext ctx = new SeedContext();

            seedSkills(db, ctx, summary);
            seedCompanies(db, ctx, summary);
            seedUsersAndFaculty(db, ctx, summary);
            seedStudents(db, ctx, summary);
            seedDrives(db, ctx, summary);
            seedApplications(db, ctx, summary);
            seedApprovalsAndRequests(db, ctx, summary);
            seedAnnouncements(db, summary);
            seedAuditLogs(db, ctx, summary);

            if (summary.length() == 0) {
                summary.append("Seed skipped: collections already contain data.");
            } else {
                summary.insert(0, "Seeded demo data -> ");
            }
        } catch (MongoException ex) {
            summary.append(" [seed warning: ").append(ex.getMessage()).append("]");
        }
        String msg = summary.toString();
        System.out.println("[MongoDB] " + msg);
        return msg;
    }

    /** Ids resolved while seeding so later collections can reference earlier ones. */
    private static final class SeedContext {
        final Map<String, String> skillIdByName = new LinkedHashMap<>();
        final Map<String, String> companyIdByName = new LinkedHashMap<>();
        final Map<String, String> userIdByUsername = new LinkedHashMap<>();
        final Map<String, String> userNameByUsername = new LinkedHashMap<>();
        final List<Student> students = new ArrayList<>();
        final List<PlacementDrive> drives = new ArrayList<>();
        final List<Document> applicationDocs = new ArrayList<>();
    }

    // ----------------------------------------------------------------- helpers

    private static String[] hash(String plain) {
        return HASH_CACHE.computeIfAbsent(plain, PasswordHasher::hashWithBCrypt);
    }

    private static String bootstrapPassword(String roleKey) {
        return DatabaseConfig.getInstance().getBootstrapPassword(roleKey);
    }

    private static Date daysAgo(int days) {
        return Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
    }

    private static String daysAgoIso(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS).toString();
    }

    private static String dateIn(int daysFromNow) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(daysFromNow).toString();
    }

    private static boolean isEmpty(MongoDatabase db, String collection) {
        return db.getCollection(collection).countDocuments() == 0;
    }

    private static void loadExistingIds(MongoDatabase db, SeedContext ctx) {
        if (ctx.skillIdByName.isEmpty()) {
            for (Document d : db.getCollection(SkillDao.COLLECTION).find()) {
                ctx.skillIdByName.put(str(d, "name"), id(d));
            }
        }
        if (ctx.companyIdByName.isEmpty()) {
            for (Document d : db.getCollection(CompanyDao.COLLECTION).find()) {
                ctx.companyIdByName.put(str(d, "name"), id(d));
            }
        }
        if (ctx.userIdByUsername.isEmpty()) {
            for (Document d : db.getCollection(UserDao.COLLECTION).find()) {
                ctx.userIdByUsername.put(str(d, "username"), id(d));
                ctx.userNameByUsername.put(str(d, "username"), str(d, "fullName"));
            }
        }
    }

    // ------------------------------------------------------------------ skills

    private static final String[][] SKILLS = {
            {"Java", "Programming Languages", "Core Java 17, OOP, Collections, Streams"},
            {"Python", "Programming Languages", "Python 3, scripting, data handling"},
            {"C / C++", "Programming Languages", "Pointers, memory management, STL"},
            {"JavaScript", "Programming Languages", "ES2022, DOM, async programming"},
            {"Data Structures & Algorithms", "Data Structures & Algorithms", "Arrays, trees, graphs, DP, complexity analysis"},
            {"Problem Solving", "Data Structures & Algorithms", "Competitive programming & coding rounds"},
            {"SQL & MySQL", "Database Management", "Joins, normalization, indexing, transactions"},
            {"MongoDB", "Database Management", "Document modeling, aggregation pipelines, indexes"},
            {"React", "Web Development", "Hooks, state management, component design"},
            {"Node.js & Express", "Web Development", "REST APIs, middleware, authentication"},
            {"Spring Boot", "Web Development", "Dependency injection, REST controllers, JPA"},
            {"Machine Learning", "AI & Machine Learning", "Supervised learning, scikit-learn, model evaluation"},
            {"Deep Learning", "AI & Machine Learning", "Neural networks, TensorFlow / PyTorch"},
            {"AWS Cloud", "Cloud Computing", "EC2, S3, IAM, Lambda fundamentals"},
            {"Docker & DevOps", "Cloud Computing", "Containers, CI/CD, Git workflows"},
            {"Linux", "Operating Systems & Networks", "Shell, permissions, processes, scripting"},
            {"Computer Networks", "Operating Systems & Networks", "OSI model, TCP/IP, HTTP, DNS"},
            {"Aptitude & Reasoning", "Communication", "Quantitative aptitude, logical reasoning"},
            {"Communication Skills", "Communication", "Group discussion, HR interview readiness"},
            {"Embedded Systems", "Operating Systems & Networks", "Microcontrollers, ARM, RTOS basics"},
            {"VLSI Design", "Programming Languages", "Verilog, digital design, synthesis"},
            {"AutoCAD & SolidWorks", "Web Development", "2D/3D mechanical & civil drafting"},
            {"MATLAB & Simulink", "AI & Machine Learning", "Signal processing, control systems simulation"},
    };

    private static void seedSkills(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        if (!isEmpty(db, SkillDao.COLLECTION)) {
            loadExistingIds(db, ctx);
            return;
        }
        List<Document> docs = new ArrayList<>();
        for (String[] s : SKILLS) {
            Skill sk = new Skill(newId(), s[0], s[1], s[2]);
            docs.add(SkillDao.toDocument(sk));
            ctx.skillIdByName.put(sk.getName(), sk.getId());
        }
        db.getCollection(SkillDao.COLLECTION).insertMany(docs);
        summary.append(docs.size()).append(" skills, ");
    }

    // --------------------------------------------------------------- companies

    private static final String[][] COMPANIES = {
            // name, industry, website, location, tier, hrName, hrEmail, description
            {"Tata Consultancy Services", "IT Services & Consulting", "https://www.tcs.com", "Hyderabad (Deccan Park, Madhapur)", "Standard IT", "Meena Iyer", "campus.hyd@tcs.com", "TCS Ninja & Digital hiring for 2026 batch across all engineering branches."},
            {"Infosys", "IT Services & Consulting", "https://www.infosys.com", "Hyderabad (Pocharam SEZ)", "Standard IT", "Rahul Nair", "campusconnect@infosys.com", "Infosys Specialist Programmer and Systems Engineer roles with InfyTQ certification pathway."},
            {"Wipro", "IT Services & Consulting", "https://www.wipro.com", "Hyderabad (Gachibowli)", "Standard IT", "Sunitha Reddy", "elite.hiring@wipro.com", "Wipro Elite National Talent Hunt for fresh engineering graduates."},
            {"Accenture", "Consulting & Technology", "https://www.accenture.com", "Hyderabad (Hitec City)", "Standard IT", "Karthik Menon", "india.campus@accenture.com", "Associate Software Engineer hiring with cloud-first training programme."},
            {"Capgemini", "IT Services & Consulting", "https://www.capgemini.com", "Hyderabad (Mindspace)", "Standard IT", "Pooja Sharma", "campus.india@capgemini.com", "Analyst and Senior Analyst roles for CSE/IT/ECE/EEE graduates."},
            {"Cognizant", "IT Services & Consulting", "https://www.cognizant.com", "Hyderabad (Manikonda)", "Standard IT", "Anil Kumar", "genc.hiring@cognizant.com", "GenC and GenC Next programmes for product-minded engineers."},
            {"Tech Mahindra", "IT Services & Telecom", "https://www.techmahindra.com", "Hyderabad (Bahadurpally)", "Standard IT", "Deepa Rao", "campus@techmahindra.com", "Associate Software Engineer roles in telecom and enterprise solutions."},
            {"Deloitte", "Consulting & Audit Technology", "https://www.deloitte.com", "Hyderabad (Gachibowli)", "Tier 1 (Dream)", "Vikram Desai", "campusrecruit@deloitte.com", "Analyst - Technology Consulting for top-quartile CGPA students."},
            {"Amazon", "Product / E-commerce & Cloud", "https://www.amazon.jobs", "Hyderabad (AWS Campus, Nanakramguda)", "Tier 2 (Super Dream)", "Sneha Kulkarni", "university.in@amazon.com", "SDE-1 hiring with online assessment and three technical rounds."},
            {"Microsoft", "Product / Software", "https://careers.microsoft.com", "Hyderabad (IDC, Gachibowli)", "Tier 2 (Super Dream)", "Arjun Mehta", "campus.idc@microsoft.com", "Software Engineer roles at Microsoft India Development Center."},
            {"ZF India", "Core Engineering / Automotive", "https://www.zf.com", "Hyderabad (ZF Technology Center)", "Standard IT", "Ravi Teja", "campus.hyd@zf.com", "Graduate Engineer Trainee for Mechanical, EEE and ECE graduates."},
            {"Cyient", "Engineering Design Services", "https://www.cyient.com", "Hyderabad (Manikonda)", "Standard IT", "Lakshmi Prasanna", "campus@cyient.com", "Design Engineer roles in aerospace, rail and utilities domains."},
    };

    private static void seedCompanies(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        if (!isEmpty(db, CompanyDao.COLLECTION)) {
            loadExistingIds(db, ctx);
            return;
        }
        List<Document> docs = new ArrayList<>();
        int i = 0;
        for (String[] c : COMPANIES) {
            Company comp = new Company();
            comp.setId(newId());
            comp.setName(c[0]);
            comp.setIndustry(c[1]);
            comp.setWebsite(c[2]);
            comp.setLocation(c[3]);
            comp.setTier(c[4]);
            comp.setHrName(c[5]);
            comp.setHrEmail(c[6]);
            comp.setHrPhone("+91 98480 " + String.format(Locale.US, "%05d", 10000 + i * 731));
            comp.setDescription(c[7]);
            comp.setStatus("ACTIVE");
            comp.setApprovalStatus("APPROVED");
            comp.setApprovedByName("Placement Administration Office");
            comp.setApprovedAt(daysAgoIso(120 - i * 3));
            comp.setCreatedAt(daysAgoIso(125 - i * 3));
            docs.add(CompanyDao.toDocument(comp));
            ctx.companyIdByName.put(comp.getName(), comp.getId());
            i++;
        }
        db.getCollection(CompanyDao.COLLECTION).insertMany(docs);
        summary.append(docs.size()).append(" companies, ");
    }

    // ---------------------------------------------------------- users/faculty

    private static void seedUsersAndFaculty(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        if (!isEmpty(db, UserDao.COLLECTION)) {
            loadExistingIds(db, ctx);
            return;
        }
        List<Document> users = new ArrayList<>();
        List<Document> facultyDocs = new ArrayList<>();

        users.add(leader("principal.sagar", "principal@gcet.edu.in", "Dr. S. Udaya Kumar (Principal)", UserRole.PRINCIPAL,
                "Office of the Principal", "Principal", "GCET_BOOTSTRAP_PRINCIPAL_PASS", ctx));
        users.add(leader("chairman.gcet", "chairman@gcet.edu.in", "Sri G. R. Ravinder Reddy (Chairman)", UserRole.CHAIRMAN,
                "Geethanjali Group of Institutions", "Chairman", "GCET_BOOTSTRAP_CHAIRMAN_PASS", ctx));
        users.add(leader("vicechairman.gcet", "vicechairman@gcet.edu.in", "Sri G. Harishchandra Reddy (Vice Chairman)", UserRole.VICE_CHAIRMAN,
                "Geethanjali Group of Institutions", "Vice Chairman", "GCET_BOOTSTRAP_VC_PASS", ctx));
        users.add(leader("placement.admin", "placements@gcet.edu.in", "Placement Administration Office", UserRole.ADMIN,
                "Training & Placement Cell", "System Administrator", "GCET_BOOTSTRAP_ADMIN_PASS", ctx));
        users.add(leader("tpo.gcet", "tpo@gcet.edu.in", "Dr. K. Srinivas (Training & Placement Officer)", UserRole.PLACEMENT_OFFICER,
                "Training & Placement Cell", "Training & Placement Officer", "GCET_BOOTSTRAP_OFFICER_PASS", ctx));

        String[][] faculty = {
                {"faculty_lavanya", "lavanya.b@gcet.edu.in", "B. Laxmi Lavanya", "CSE", "Associate Professor & Placement Coordinator", "GCET-F-1021", "CSE,CSE-AIML,CSE-DS"},
                {"faculty_aparna", "aparna.p@gcet.edu.in", "P. Aparna", "IT", "Assistant Professor & Placement Coordinator", "GCET-F-1134", "CSE-CS,IT"},
                {"faculty_rajashakar", "rajashakar.s@gcet.edu.in", "S. Raja Shakar Reddy", "ECE", "Professor & Core Branches Coordinator", "GCET-F-0987", "EEE,ECE,MECH,CIVIL"},
        };
        String facultyPass = bootstrapPassword("GCET_BOOTSTRAP_FACULTY_PASS");
        for (String[] f : faculty) {
            User u = new User();
            u.setId(newId());
            u.setUsername(f[0]);
            u.setEmail(f[1]);
            u.setFullName(f[2]);
            u.setRole(UserRole.FACULTY);
            u.setDepartment(f[3]);
            u.setDesignation(f[4]);
            u.setEmployeeId(f[5]);
            u.setPhone("+91 98490 " + (20000 + users.size() * 37));
            u.setStatus("ACTIVE");
            u.setActive(true);
            u.setAuthorizedDepartments(List.of(f[6].split(",")));
            u.setApprovedByName("Placement Administration Office");
            u.setApprovedAt(daysAgoIso(200));
            u.setCreatedAt(daysAgoIso(210));
            String[] h = hash(facultyPass);
            users.add(UserDao.toDocument(u, h[0], h[1]));
            ctx.userIdByUsername.put(u.getUsername(), u.getId());
            ctx.userNameByUsername.put(u.getUsername(), u.getFullName());

            Faculty fac = new Faculty();
            fac.setId(newId());
            fac.setUserId(u.getId());
            fac.setEmployeeId(f[5]);
            fac.setFullName(f[2]);
            fac.setDepartment(f[3]);
            fac.setDesignation(f[4]);
            fac.setEmail(f[1]);
            fac.setPhone(u.getPhone());
            fac.setAuthorizedDepartments(u.getAuthorizedDepartments());
            fac.setFacultyApproved(true);
            fac.setAdminApproved(true);
            fac.setStatus("ACTIVE");
            fac.setApprovedByName("Placement Administration Office");
            fac.setCreatedAt(daysAgoIso(210));
            facultyDocs.add(FacultyDao.toDocument(fac));
        }

        // One pending faculty registration for the Approval Center demo
        User pending = new User();
        pending.setId(newId());
        pending.setUsername("gcet-f-1377");
        pending.setEmail("srikanth.m@gcet.edu.in");
        pending.setFullName("M. Srikanth");
        pending.setRole(UserRole.FACULTY);
        pending.setDepartment("MECH");
        pending.setDesignation("Assistant Professor");
        pending.setEmployeeId("GCET-F-1377");
        pending.setStatus("PENDING_APPROVAL");
        pending.setActive(false);
        pending.setCreatedAt(daysAgoIso(2));
        String[] ph = hash(facultyPass);
        users.add(UserDao.toDocument(pending, ph[0], ph[1]));
        ctx.userIdByUsername.put(pending.getUsername(), pending.getId());
        ctx.userNameByUsername.put(pending.getUsername(), pending.getFullName());
        Faculty pf = new Faculty();
        pf.setId(newId());
        pf.setUserId(pending.getId());
        pf.setEmployeeId("GCET-F-1377");
        pf.setFullName("M. Srikanth");
        pf.setDepartment("MECH");
        pf.setDesignation("Assistant Professor");
        pf.setEmail("srikanth.m@gcet.edu.in");
        pf.setPhone("+91 98491 44321");
        pf.setAuthorizedDepartments(List.of("MECH"));
        pf.setFacultyApproved(true);
        pf.setAdminApproved(false);
        pf.setStatus("PENDING_APPROVAL");
        pf.setCreatedAt(daysAgoIso(2));
        facultyDocs.add(FacultyDao.toDocument(pf));

        // Recruiter accounts linked to companies by ObjectId reference
        String recruiterPass = bootstrapPassword("GCET_BOOTSTRAP_RECRUITER_PASS");
        for (String[] c : COMPANIES) {
            String slug = c[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (slug.length() > 15) slug = slug.substring(0, 15);
            User r = new User();
            r.setId(newId());
            r.setUsername("recruiter_" + slug);
            r.setEmail(c[6]);
            r.setFullName(c[5] + " (" + c[0] + ")");
            r.setRole(UserRole.COMPANY);
            r.setDepartment(c[0]);
            r.setDesignation("Campus Recruitment Lead");
            r.setCompanyId(ctx.companyIdByName.get(c[0]));
            r.setCompanyName(c[0]);
            r.setStatus("APPROVED");
            r.setActive(true);
            r.setApprovedByName("Placement Administration Office");
            r.setApprovedAt(daysAgoIso(118));
            r.setCreatedAt(daysAgoIso(124));
            String[] h = hash(recruiterPass);
            users.add(UserDao.toDocument(r, h[0], h[1]));
            ctx.userIdByUsername.put(r.getUsername(), r.getId());
            ctx.userNameByUsername.put(r.getUsername(), r.getFullName());
        }

        db.getCollection(UserDao.COLLECTION).insertMany(users);
        if (isEmpty(db, FacultyDao.COLLECTION)) {
            db.getCollection(FacultyDao.COLLECTION).insertMany(facultyDocs);
        }
        summary.append(users.size()).append(" users (").append(facultyDocs.size()).append(" faculty profiles), ");
    }

    private static Document leader(String username, String email, String fullName, UserRole role,
                                   String department, String designation, String passKey, SeedContext ctx) {
        User u = new User();
        u.setId(newId());
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(role);
        u.setDepartment(department);
        u.setDesignation(designation);
        u.setPhone("+91 91820 58187");
        u.setStatus("ACTIVE");
        u.setActive(true);
        u.setCreatedAt(daysAgoIso(365));
        String[] h = hash(bootstrapPassword(passKey));
        ctx.userIdByUsername.put(username, u.getId());
        ctx.userNameByUsername.put(username, fullName);
        return UserDao.toDocument(u, h[0], h[1]);
    }

    // ---------------------------------------------------------------- students

    private static final String[][] STUDENTS = {
            // roll, name, branch, cgpa, backlogs, placement, package, company, skills(name:level[:v]) , approval
            {"22R11A0501", "Akhil Reddy Gaddam", "CSE", "9.12", "0", "PLACED", "44.0", "Amazon", "Java:5:v,Data Structures & Algorithms:5:v,Problem Solving:5,SQL & MySQL:4:v,AWS Cloud:3", "APPROVED"},
            {"22R11A0502", "Bhavana Sri Kolluri", "CSE", "8.74", "0", "PLACED", "9.5", "Infosys", "Python:4:v,Java:4,Data Structures & Algorithms:4:v,MongoDB:3,React:3", "APPROVED"},
            {"22R11A0503", "Charan Teja Vangala", "CSE", "7.95", "0", "PLACED", "7.6", "Deloitte", "Java:4:v,SQL & MySQL:4:v,Spring Boot:3,Communication Skills:4:v,Aptitude & Reasoning:4", "APPROVED"},
            {"22R11A0504", "Divya Madhuri Pulipati", "CSE", "8.31", "0", "SHORTLISTED", "", "", "Python:4:v,Machine Learning:4,Deep Learning:3,SQL & MySQL:3:v,Communication Skills:3", "APPROVED"},
            {"22R11A0505", "Eshwar Sai Kandula", "CSE", "6.48", "1", "UNPLACED", "", "", "C / C++:3,Java:2,Data Structures & Algorithms:2,Aptitude & Reasoning:3,Communication Skills:3", "APPROVED"},
            {"22R11A0506", "Fathima Begum Shaik", "CSE", "9.36", "0", "PLACED", "50.0", "Microsoft", "C / C++:5:v,Data Structures & Algorithms:5:v,Problem Solving:5:v,Java:4,Computer Networks:4:v", "APPROVED"},
            {"22R11A0507", "Gowtham Krishna Mallela", "CSE", "7.12", "0", "PLACED", "3.36", "Tata Consultancy Services", "Java:3:v,SQL & MySQL:3,Aptitude & Reasoning:4:v,Communication Skills:3", "APPROVED"},
            {"22R11A0508", "Harini Chowdary Yarlagadda", "CSE", "8.02", "0", "UNPLACED", "", "", "JavaScript:4:v,React:4:v,Node.js & Express:4,MongoDB:4:v,Docker & DevOps:2", "APPROVED"},
            {"22R11A1201", "Ishaan Varma Penmetsa", "IT", "8.55", "0", "PLACED", "6.75", "Cognizant", "Java:4:v,Spring Boot:4,SQL & MySQL:4:v,Linux:3,Aptitude & Reasoning:4", "APPROVED"},
            {"22R11A1202", "Jyothsna Lakshmi Bandi", "IT", "7.68", "0", "SHORTLISTED", "", "", "Python:4,Node.js & Express:3,React:3:v,Communication Skills:4:v", "APPROVED"},
            {"22R11A1203", "Karthik Sai Nallamothu", "IT", "6.92", "0", "PLACED", "3.5", "Wipro", "Java:3,SQL & MySQL:3:v,Aptitude & Reasoning:3,Communication Skills:3", "APPROVED"},
            {"22R11A1204", "Lahari Devi Thota", "IT", "8.9", "0", "UNPLACED", "", "", "Java:4:v,Data Structures & Algorithms:4:v,Problem Solving:4,AWS Cloud:3:v,Docker & DevOps:3", "APPROVED"},
            {"22R11A6601", "Manoj Kumar Adapa", "CSE - AIML", "8.46", "0", "PLACED", "9.5", "Infosys", "Python:5:v,Machine Learning:4:v,Deep Learning:4,SQL & MySQL:3,Data Structures & Algorithms:3", "APPROVED"},
            {"22R11A6602", "Nandini Priya Sunkara", "CSE - AIML", "9.05", "0", "SHORTLISTED", "", "", "Python:5:v,Machine Learning:5:v,Deep Learning:4:v,MATLAB & Simulink:3,Problem Solving:4", "APPROVED"},
            {"22R11A6603", "Om Prakash Yadav", "CSE - AIML", "7.2", "1", "UNPLACED", "", "", "Python:3,Machine Learning:3,Aptitude & Reasoning:2", "APPROVED"},
            {"22R11A6604", "Pranathi Reddy Mekala", "CSE - AIML", "8.11", "0", "PLACED", "4.5", "Accenture", "Python:4:v,Java:3,Machine Learning:3:v,Communication Skills:4,Aptitude & Reasoning:4:v", "APPROVED"},
            {"22R11A6701", "Rohith Kumar Gunda", "CSE - DS", "8.67", "0", "PLACED", "7.6", "Deloitte", "Python:4:v,SQL & MySQL:5:v,MongoDB:4:v,Machine Learning:3,Communication Skills:4", "APPROVED"},
            {"22R11A6702", "Sahithi Rao Vuppala", "CSE - DS", "7.84", "0", "UNPLACED", "", "", "Python:4,SQL & MySQL:4:v,Machine Learning:3,Aptitude & Reasoning:3,Communication Skills:4:v", "APPROVED"},
            {"22R11A6703", "Tarun Sai Boddu", "CSE - DS", "6.35", "2", "UNPLACED", "", "", "Python:2,SQL & MySQL:2,Communication Skills:2", "APPROVED"},
            {"22R11A0401", "Uday Kiran Pasupuleti", "ECE", "8.23", "0", "PLACED", "4.25", "Capgemini", "C / C++:4:v,Embedded Systems:4:v,Computer Networks:3,Aptitude & Reasoning:4", "APPROVED"},
            {"22R11A0402", "Varsha Reddy Kota", "ECE", "7.56", "0", "UNPLACED", "", "", "VLSI Design:4:v,MATLAB & Simulink:4,C / C++:3,Communication Skills:3,Aptitude & Reasoning:3", "APPROVED"},
            {"22R11A0403", "Wasim Akram Mohammed", "ECE", "6.88", "0", "PLACED", "3.36", "Tata Consultancy Services", "C / C++:3,Java:2,Aptitude & Reasoning:4:v,Communication Skills:3:v", "APPROVED"},
            {"22R11A0404", "Yashwanth Naidu Bellamkonda", "ECE", "9.01", "0", "SHORTLISTED", "", "", "Embedded Systems:5:v,C / C++:4:v,Linux:4,Computer Networks:4:v,Problem Solving:3", "APPROVED"},
            {"22R11A0201", "Anjali Devi Kurra", "EEE", "7.74", "0", "PLACED", "5.5", "ZF India", "MATLAB & Simulink:4:v,Embedded Systems:3,C / C++:3,Aptitude & Reasoning:3", "APPROVED"},
            {"22R11A0202", "Bharath Chandra Palle", "EEE", "6.61", "1", "UNPLACED", "", "", "MATLAB & Simulink:3,C / C++:2,Communication Skills:2", "APPROVED"},
            {"22R11A0301", "Chaitanya Krishna Mudavath", "MECH", "8.09", "0", "PLACED", "5.5", "ZF India", "AutoCAD & SolidWorks:5:v,MATLAB & Simulink:3,Aptitude & Reasoning:4:v,Communication Skills:3", "APPROVED"},
            {"22R11A0302", "Deepika Rani Gajula", "MECH", "7.33", "0", "UNPLACED", "", "", "AutoCAD & SolidWorks:4:v,Python:2,Aptitude & Reasoning:3", "APPROVED"},
            {"22R11A0303", "Ganesh Babu Ravula", "MECH", "6.5", "0", "UNPLACED", "", "", "AutoCAD & SolidWorks:3,Communication Skills:2,Aptitude & Reasoning:2", "APPROVED"},
            {"22R11A0101", "Hemanth Kumar Siddam", "CIVIL", "7.91", "0", "PLACED", "4.0", "Cyient", "AutoCAD & SolidWorks:4:v,Aptitude & Reasoning:4,Communication Skills:4:v", "APPROVED"},
            {"22R11A0102", "Indira Priyadarshini Katta", "CIVIL", "8.38", "0", "UNPLACED", "", "", "AutoCAD & SolidWorks:5:v,MATLAB & Simulink:3,Communication Skills:4", "APPROVED"},
            {"23R11A0511", "Jaswanth Reddy Annam", "CSE", "8.2", "0", "UNPLACED", "", "", "Java:3,Data Structures & Algorithms:3,React:2", "PENDING_APPROVAL"},
            {"23R11A1212", "Keerthana Sai Pendyala", "IT", "7.9", "0", "UNPLACED", "", "", "Python:3,SQL & MySQL:3,Communication Skills:3", "PENDING_APPROVAL"},
    };

    private static void seedStudents(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        if (!isEmpty(db, StudentDao.COLLECTION)) {
            for (Document d : db.getCollection(StudentDao.COLLECTION).find()) {
                ctx.students.add(StudentDao.mapStudent(d));
            }
            return;
        }
        loadExistingIds(db, ctx);
        String studentPass = bootstrapPassword("GCET_BOOTSTRAP_STUDENT_SEED_PASS");
        String[] studentHash = hash(studentPass);
        String lavanyaId = ctx.userIdByUsername.get("faculty_lavanya");
        String aparnaId = ctx.userIdByUsername.get("faculty_aparna");
        String rajaId = ctx.userIdByUsername.get("faculty_rajashakar");

        List<Document> studentDocs = new ArrayList<>();
        List<Document> userDocs = new ArrayList<>();
        int idx = 0;
        for (String[] row : STUDENTS) {
            idx++;
            Student s = new Student();
            s.setId(newId());
            s.setUserId(newId());
            s.setRollNumber(row[0]);
            s.setFullName(row[1]);
            s.setBranch(row[2]);
            s.setCgpa(Double.parseDouble(row[3]));
            s.setBacklogs(Integer.parseInt(row[4]));
            s.setPlacementStatus(row[5]);
            if (!row[6].isBlank()) s.setPlacedPackageLpa(Double.parseDouble(row[6]));
            if (!row[7].isBlank()) s.setPlacedCompanyId(ctx.companyIdByName.get(row[7]));
            s.setApprovalStatus(row[9]);
            boolean thirdYear = row[0].startsWith("23");
            s.setGraduationYear(thirdYear ? 2027 : 2026);
            s.setYearOfStudy(thirdYear ? 3 : 4);
            s.setAcademicYear(s.getYearOfStudy());
            s.setSection(idx % 2 == 0 ? "A" : "B");
            s.setTenthPercentage(Math.round((78 + RNG.nextDouble() * 20) * 10) / 10.0);
            s.setInterPercentage(Math.round((80 + RNG.nextDouble() * 18) * 10) / 10.0);
            s.setEmail(row[0].toLowerCase(Locale.ROOT) + "@gcet.edu.in");
            s.setPhone("+91 9" + String.format(Locale.US, "%09d", 100000000 + (long) idx * 7351));
            s.setStudentIdCard("GCET-" + s.getGraduationYear() + "-" + row[0].substring(row[0].length() - 4));
            s.setCreatedAt(daysAgoIso(300 - idx * 2));

            String coordinatorId;
            String coordinatorName;
            String norm = edu.gcet.placementhub.utils.DeptNormalizer.normalizeDeptCode(s.getBranch());
            if (List.of("CSE", "CSE-AIML", "CSE-DS").contains(norm)) {
                coordinatorId = lavanyaId;
                coordinatorName = "B. Laxmi Lavanya";
            } else if (List.of("CSE-CS", "IT").contains(norm)) {
                coordinatorId = aparnaId;
                coordinatorName = "P. Aparna";
            } else {
                coordinatorId = rajaId;
                coordinatorName = "S. Raja Shakar Reddy";
            }
            if ("APPROVED".equals(row[9])) {
                s.setApprovedByFacultyId(coordinatorId);
                s.setApprovedByFacultyName(coordinatorName);
                s.setApprovedAt(daysAgoIso(295 - idx * 2));
            }

            // Embedded skills
            List<StudentSkill> skills = new ArrayList<>();
            for (String part : row[8].split(",")) {
                String[] p = part.split(":");
                String skillId = ctx.skillIdByName.get(p[0]);
                if (skillId == null) continue;
                StudentSkill ss = new StudentSkill();
                ss.setStudentId(s.getId());
                ss.setSkillId(skillId);
                ss.setSkillName(p[0]);
                ss.setProficiency(Integer.parseInt(p[1]));
                boolean verified = p.length > 2 && "v".equals(p[2]);
                ss.setVerifiedByFaculty(verified);
                if (verified) {
                    ss.setVerifiedByFacultyId(coordinatorId);
                    ss.setVerifiedAt(daysAgoIso(60 + idx));
                }
                skills.add(ss);
            }
            s.setSkills(skills);
            s.setProjects(sampleProjects(s, idx));
            s.setCertifications(sampleCertifications(s, idx));
            s.setInternships(sampleInternships(s, idx));

            studentDocs.add(StudentDao.toDocument(s));
            ctx.students.add(s);

            User u = new User();
            u.setId(s.getUserId());
            u.setUsername(s.getRollNumber());
            u.setEmail(s.getEmail());
            u.setFullName(s.getFullName());
            u.setRole(UserRole.STUDENT);
            u.setDepartment(s.getBranch());
            u.setStudentRollNumber(s.getRollNumber());
            u.setPhone(s.getPhone());
            boolean approved = "APPROVED".equals(row[9]);
            u.setStatus(approved ? "APPROVED" : "PENDING_APPROVAL");
            u.setActive(approved);
            if (approved) {
                u.setApprovedBy(coordinatorId);
                u.setApprovedByName(coordinatorName);
                u.setApprovedAt(s.getApprovedAt());
            }
            u.setCreatedAt(s.getCreatedAt());
            userDocs.add(UserDao.toDocument(u, studentHash[0], studentHash[1]));
            ctx.userIdByUsername.put(u.getUsername(), u.getId());
            ctx.userNameByUsername.put(u.getUsername(), u.getFullName());
        }
        db.getCollection(StudentDao.COLLECTION).insertMany(studentDocs);
        db.getCollection(UserDao.COLLECTION).insertMany(userDocs);
        summary.append(studentDocs.size()).append(" students (+login accounts), ");
    }

    private static List<StudentProject> sampleProjects(Student s, int idx) {
        List<StudentProject> list = new ArrayList<>();
        String norm = edu.gcet.placementhub.utils.DeptNormalizer.normalizeDeptCode(s.getBranch());
        String[][] pool;
        if (norm.startsWith("CSE") || "IT".equals(norm)) {
            pool = new String[][]{
                    {"GCET Placement Hub (MongoDB Edition)", "Document-oriented placement portal with aggregation analytics.", "Java 17,Swing,MongoDB Atlas"},
                    {"Smart Attendance using Face Recognition", "OpenCV + Flask attendance system deployed in CSE labs.", "Python,OpenCV,Flask"},
                    {"Campus Marketplace", "MERN stack portal for buying and selling used textbooks.", "React,Node.js,MongoDB"},
                    {"Hostel Mess Feedback Analytics", "Sentiment analysis dashboard over 2,000 student reviews.", "Python,scikit-learn,Streamlit"},
            };
        } else if ("ECE".equals(norm) || "EEE".equals(norm)) {
            pool = new String[][]{
                    {"IoT Smart Energy Meter", "ESP32 based meter streaming readings to a cloud dashboard.", "ESP32,MQTT,Node.js"},
                    {"Solar MPPT Charge Controller", "Perturb & observe MPPT implemented on Arduino.", "Arduino,MATLAB"},
                    {"FPGA Traffic Signal Controller", "Verilog state machine synthesized on Xilinx Spartan-6.", "Verilog,Vivado"},
            };
        } else {
            pool = new String[][]{
                    {"Automated Rainwater Harvesting Design", "Hydraulic design for the GCET hostel block.", "AutoCAD,Excel"},
                    {"Composite Brake Pad Analysis", "FEA study of brake pads using ANSYS Workbench.", "SolidWorks,ANSYS"},
                    {"Low-Cost Pedestrian Bridge", "Structural design and cost estimation for Cheeryal village.", "AutoCAD,STAAD.Pro"},
            };
        }
        int count = 1 + (idx % 2);
        for (int i = 0; i < count; i++) {
            String[] p = pool[(idx + i) % pool.length];
            StudentProject sp = new StudentProject();
            sp.setId(newId());
            sp.setStudentId(s.getId());
            sp.setTitle(p[0]);
            sp.setDescription(p[1]);
            sp.setTechnologies(List.of(p[2].split(",")));
            sp.setLink("https://github.com/gcet-" + s.getRollNumber().toLowerCase(Locale.ROOT) + "/" + p[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
            sp.setDuration((2 + i) + " months");
            sp.setCreatedAt(daysAgoIso(90 + idx + i * 15));
            list.add(sp);
        }
        return list;
    }

    private static List<StudentCertification> sampleCertifications(Student s, int idx) {
        List<StudentCertification> list = new ArrayList<>();
        String[][] pool = {
                {"AWS Certified Cloud Practitioner", "Amazon Web Services"},
                {"Oracle Certified Associate, Java SE", "Oracle"},
                {"MongoDB Associate Developer", "MongoDB University"},
                {"Google Data Analytics Professional", "Coursera / Google"},
                {"NPTEL Data Structures & Algorithms (Elite)", "IIT Madras / NPTEL"},
                {"Cisco CCNA: Introduction to Networks", "Cisco Networking Academy"},
                {"AutoCAD Certified User", "Autodesk"},
        };
        if (idx % 3 == 0) return list;
        int count = 1 + (idx % 2);
        for (int i = 0; i < count; i++) {
            String[] c = pool[(idx * 2 + i) % pool.length];
            StudentCertification sc = new StudentCertification();
            sc.setId(newId());
            sc.setStudentId(s.getId());
            sc.setName(c[0]);
            sc.setIssuingOrg(c[1]);
            sc.setIssueDate(LocalDate.now().minusDays(120 + idx * 3L + i * 40L).toString());
            sc.setCredentialId("GCET-" + s.getRollNumber().substring(s.getRollNumber().length() - 4) + "-" + (1000 + idx * 7 + i));
            sc.setCreatedAt(daysAgoIso(100 + idx));
            list.add(sc);
        }
        return list;
    }

    private static List<StudentInternship> sampleInternships(Student s, int idx) {
        List<StudentInternship> list = new ArrayList<>();
        if (idx % 2 == 1 && !"PLACED".equals(s.getPlacementStatus())) return list;
        String[][] pool = {
                {"Infosys Springboard", "Software Development Intern", "8 weeks", "Built an employee leave-tracking module with Spring Boot and MySQL."},
                {"Cyient", "Engineering Intern", "6 weeks", "Assisted with CAD drafting and design validation."},
                {"TCS iON", "Data Analytics Intern", "6 weeks", "Prepared dashboards on placement statistics using Power BI."},
                {"GCET Innovation Cell", "Research Intern", "10 weeks", "Prototyped an IoT lab-monitoring kit for the ECE department."},
                {"Zoho Corporation", "Web Development Intern", "8 weeks", "Implemented REST endpoints and React screens for an internal tool."},
        };
        String[] p = pool[idx % pool.length];
        StudentInternship si = new StudentInternship();
        si.setId(newId());
        si.setStudentId(s.getId());
        si.setCompanyName(p[0]);
        si.setRole(p[1]);
        si.setDuration(p[2]);
        si.setDescription(p[3]);
        si.setCreatedAt(daysAgoIso(150 + idx));
        list.add(si);
        return list;
    }

    // ------------------------------------------------------------------ drives

    private static final Object[][] DRIVES = {
            // company, role, package, minCgpa, maxBacklogs, branches, status, driveDate offset, deadline offset, positions, skills "name:level:m"
            {"Tata Consultancy Services", "TCS Ninja - Assistant System Engineer", 3.36, 6.0, 0, "CSE,IT,CSE - AIML,CSE - DS,ECE,EEE,MECH,CIVIL", "OPEN", 14, 7, 60, "Aptitude & Reasoning:3:m,Communication Skills:3:m,Java:2"},
            {"Infosys", "Specialist Programmer", 9.5, 7.0, 0, "CSE,IT,CSE - AIML,CSE - DS", "OPEN", 21, 10, 15, "Data Structures & Algorithms:4:m,Problem Solving:4:m,Python:3,Java:3"},
            {"Wipro", "Elite National Talent Hunt - Project Engineer", 3.5, 6.0, 0, "CSE,IT,CSE - AIML,CSE - DS,ECE,EEE", "OPEN", 18, 9, 50, "Aptitude & Reasoning:3:m,Communication Skills:3:m,SQL & MySQL:2"},
            {"Accenture", "Associate Software Engineer", 4.5, 6.5, 0, "CSE,IT,CSE - AIML,CSE - DS,ECE,EEE,MECH,CIVIL", "UPCOMING", 35, 25, 40, "Communication Skills:3:m,Aptitude & Reasoning:3:m,Java:3,SQL & MySQL:3"},
            {"Capgemini", "Analyst - Exceller Programme", 4.25, 6.0, 0, "CSE,IT,ECE,EEE", "OPEN", 12, 5, 30, "Aptitude & Reasoning:3:m,Java:3,SQL & MySQL:3"},
            {"Cognizant", "GenC Next - Programmer Analyst", 6.75, 7.0, 0, "CSE,IT,CSE - AIML,CSE - DS", "UPCOMING", 40, 28, 25, "Java:4:m,Spring Boot:3:m,SQL & MySQL:3,Data Structures & Algorithms:3"},
            {"Deloitte", "Analyst - Technology Consulting", 7.6, 7.0, 0, "CSE,IT,CSE - AIML,CSE - DS,ECE", "OPEN", 25, 15, 12, "SQL & MySQL:4:m,Communication Skills:4:m,Python:3,Aptitude & Reasoning:4"},
            {"Amazon", "Software Development Engineer I", 44.0, 8.0, 0, "CSE,IT,CSE - AIML,CSE - DS", "UPCOMING", 45, 30, 4, "Data Structures & Algorithms:5:m,Problem Solving:5:m,Java:4,AWS Cloud:3"},
            {"Microsoft", "Software Engineer - IDC Hyderabad", 50.0, 8.5, 0, "CSE,IT,CSE - AIML,CSE - DS", "OPEN", 30, 20, 3, "Data Structures & Algorithms:5:m,Problem Solving:5:m,C / C++:4,Computer Networks:3"},
            {"ZF India", "Graduate Engineer Trainee", 5.5, 6.5, 0, "MECH,EEE,ECE", "OPEN", 20, 12, 10, "MATLAB & Simulink:3:m,AutoCAD & SolidWorks:3,Embedded Systems:3,Aptitude & Reasoning:3:m"},
            {"Cyient", "Design Engineer", 4.0, 6.0, 1, "ECE,EEE,MECH,CIVIL", "COMPLETED", -45, -60, 15, "AutoCAD & SolidWorks:3:m,Communication Skills:3,Aptitude & Reasoning:3"},
            {"Tech Mahindra", "Associate Software Engineer", 3.75, 6.0, 1, "CSE,IT,CSE - AIML,CSE - DS,ECE,EEE", "PLANNED", 60, 50, 35, "Aptitude & Reasoning:3:m,Communication Skills:3:m,Java:2"},
    };

    private static void seedDrives(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        if (!isEmpty(db, DriveDao.COLLECTION)) {
            for (Document d : db.getCollection(DriveDao.COLLECTION).find()) {
                ctx.drives.add(DriveDao.mapDrive(d));
            }
            return;
        }
        loadExistingIds(db, ctx);
        List<Document> docs = new ArrayList<>();
        int i = 0;
        for (Object[] row : DRIVES) {
            i++;
            PlacementDrive d = new PlacementDrive();
            d.setId(newId());
            String companyName = (String) row[0];
            d.setCompanyId(ctx.companyIdByName.get(companyName));
            d.setCompanyName(companyName);
            for (String[] c : COMPANIES) {
                if (c[0].equals(companyName)) d.setCompanyTier(c[4]);
            }
            d.setJobRole((String) row[1]);
            d.setJobDescription(companyName + " campus recruitment drive for the " + row[1]
                    + " role. Selection: online assessment, technical interview and HR discussion at the GCET Placement Cell.");
            d.setSalaryPackageLpa((Double) row[2]);
            d.setMinCgpa((Double) row[3]);
            d.setMaxBacklogsAllowed((Integer) row[4]);
            d.setEligibleBranches(List.of(((String) row[5]).split(",")));
            d.setStatus((String) row[6]);
            d.setDriveDate(dateIn((Integer) row[7]));
            d.setApplicationDeadline(dateIn((Integer) row[8]));
            d.setAvailablePositions((Integer) row[9]);
            d.setTargetGraduationYear(2026);
            d.setSelectionProcess("Online Assessment -> Technical Interview -> HR Interview");
            d.setCreatedAt(daysAgoIso(70 - i * 4));
            List<DriveRequiredSkill> reqs = new ArrayList<>();
            for (String part : ((String) row[10]).split(",")) {
                String[] p = part.split(":");
                String skillId = ctx.skillIdByName.get(p[0]);
                if (skillId == null) continue;
                DriveRequiredSkill r = new DriveRequiredSkill(skillId, Integer.parseInt(p[1]), p.length > 2 && "m".equals(p[2]));
                r.setSkillName(p[0]);
                reqs.add(r);
            }
            d.setRequiredSkills(reqs);
            docs.add(DriveDao.toDocument(d));
            ctx.drives.add(d);
        }
        db.getCollection(DriveDao.COLLECTION).insertMany(docs);
        for (PlacementDrive d : ctx.drives) {
            db.getCollection(CompanyDao.COLLECTION).updateOne(idFilter(d.getCompanyId()),
                    new Document("$inc", new Document("totalDrivesConducted", 1)));
        }
        summary.append(docs.size()).append(" drives, ");
    }

    // ------------------------------------------------------------ applications

    private static final ApplicationStage[] STAGE_ORDER = {
            ApplicationStage.APPLICATION_SUBMITTED,
            ApplicationStage.APPLICATION_REVIEWED,
            ApplicationStage.SHORTLISTED,
            ApplicationStage.ONLINE_ASSESSMENT,
            ApplicationStage.TECHNICAL_INTERVIEW,
            ApplicationStage.HR_INTERVIEW,
            ApplicationStage.SELECTED,
            ApplicationStage.OFFERED,
            ApplicationStage.JOINED,
    };

    private static void seedApplications(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        if (!isEmpty(db, ApplicationDao.COLLECTION) || ctx.students.isEmpty() || ctx.drives.isEmpty()) {
            return;
        }
        loadExistingIds(db, ctx);
        Map<String, PlacementDrive> driveByCompany = new LinkedHashMap<>();
        for (PlacementDrive d : ctx.drives) {
            driveByCompany.putIfAbsent(d.getCompanyName(), d);
        }
        Map<String, String> companyNameById = new HashMap<>();
        for (Map.Entry<String, String> e : ctx.companyIdByName.entrySet()) companyNameById.put(e.getValue(), e.getKey());

        List<Document> docs = new ArrayList<>();
        int n = 0;
        for (Student s : ctx.students) {
            if (!"APPROVED".equalsIgnoreCase(s.getApprovalStatus())) continue;
            n++;
            List<PlacementDrive> chosen = new ArrayList<>();

            // Placed students: an OFFERED/JOINED application at their placement company
            if ("PLACED".equalsIgnoreCase(s.getPlacementStatus()) && s.getPlacedCompanyId() != null) {
                PlacementDrive placedDrive = driveByCompany.get(companyNameById.get(s.getPlacedCompanyId()));
                if (placedDrive != null) {
                    ApplicationStage finalStage = (n % 3 == 0) ? ApplicationStage.JOINED : ApplicationStage.OFFERED;
                    docs.add(buildApplication(s, placedDrive, finalStage, s.getPlacedPackageLpa(), null, 40 + n, ctx));
                    chosen.add(placedDrive);
                }
            }
            // Everyone applies to 1-3 additional eligible drives with varied stages.
            // Drives from index 6 onwards (Deloitte, Amazon, Microsoft, ZF, Cyient, Tech Mahindra) are left
            // without auto-generated applications so students can apply live during the presentation.
            int extra = 1 + (n % 3);
            for (PlacementDrive d : ctx.drives) {
                if (chosen.size() >= extra + (chosen.isEmpty() ? 0 : 1)) break;
                if (chosen.contains(d)) continue;
                if (ctx.drives.indexOf(d) >= 6 && !"COMPLETED".equals(d.getStatus())) continue;
                if (!"OPEN".equals(d.getStatus()) && !"UPCOMING".equals(d.getStatus()) && !"COMPLETED".equals(d.getStatus())) continue;
                boolean branchOk = false;
                String norm = edu.gcet.placementhub.utils.DeptNormalizer.normalizeDeptCode(s.getBranch());
                for (String b : d.getEligibleBranches()) {
                    if (edu.gcet.placementhub.utils.DeptNormalizer.normalizeDeptCode(b).equals(norm)) branchOk = true;
                }
                if (!branchOk || s.getCgpa() < d.getMinCgpa() || s.getBacklogs() > d.getMaxBacklogsAllowed()) continue;

                ApplicationStage stage;
                String rejection = null;
                int pick = (n * 7 + chosen.size() * 3) % 9;
                if ("SHORTLISTED".equalsIgnoreCase(s.getPlacementStatus())) {
                    stage = chosen.isEmpty() ? ApplicationStage.TECHNICAL_INTERVIEW : ApplicationStage.SHORTLISTED;
                } else if (pick <= 2) {
                    stage = ApplicationStage.APPLICATION_SUBMITTED;
                } else if (pick <= 4) {
                    stage = ApplicationStage.APPLICATION_REVIEWED;
                } else if (pick == 5) {
                    stage = ApplicationStage.ONLINE_ASSESSMENT;
                } else if (pick == 6) {
                    stage = ApplicationStage.REJECTED;
                    rejection = "Did not clear the online assessment cut-off (coding round).";
                } else if (pick == 7) {
                    stage = ApplicationStage.WITHDRAWN;
                } else {
                    stage = ApplicationStage.HR_INTERVIEW;
                }
                if ("PLACED".equalsIgnoreCase(s.getPlacementStatus()) && stage != ApplicationStage.REJECTED && stage != ApplicationStage.WITHDRAWN) {
                    stage = ApplicationStage.WITHDRAWN; // already placed elsewhere
                }
                docs.add(buildApplication(s, d, stage, null, rejection, 25 + n + chosen.size() * 2, ctx));
                chosen.add(d);
            }
        }
        if (!docs.isEmpty()) {
            db.getCollection(ApplicationDao.COLLECTION).insertMany(docs);
            ctx.applicationDocs.addAll(docs);
        }
        summary.append(docs.size()).append(" applications, ");
    }

    private static Document buildApplication(Student s, PlacementDrive d, ApplicationStage finalStage, Double offer,
                                             String rejection, int startDaysAgo, SeedContext ctx) {
        List<Document> history = new ArrayList<>();
        int day = startDaysAgo;
        String recruiter = "Campus Recruitment Lead (" + d.getCompanyName() + ")";
        String tpo = ctx.userNameByUsername.getOrDefault("tpo.gcet", "Training & Placement Officer");

        if (finalStage == ApplicationStage.REJECTED || finalStage == ApplicationStage.WITHDRAWN) {
            history.add(stageDoc(ApplicationStage.APPLICATION_SUBMITTED, s.getFullName(), "Application submitted via GCET Placement Hub", day));
            day -= 3;
            history.add(stageDoc(ApplicationStage.APPLICATION_REVIEWED, tpo, "Profile verified by Placement Cell", day));
            day -= 4;
            if (finalStage == ApplicationStage.REJECTED) {
                history.add(stageDoc(ApplicationStage.ONLINE_ASSESSMENT, recruiter, "Online assessment attempted", day));
                day -= 2;
                history.add(stageDoc(ApplicationStage.REJECTED, recruiter, rejection, day));
            } else {
                history.add(stageDoc(ApplicationStage.WITHDRAWN, s.getFullName(), "Withdrawn by candidate (accepted another offer)", day));
            }
        } else {
            for (ApplicationStage st : STAGE_ORDER) {
                String actor = switch (st) {
                    case APPLICATION_SUBMITTED -> s.getFullName();
                    case APPLICATION_REVIEWED, SHORTLISTED -> tpo;
                    default -> recruiter;
                };
                String remark = switch (st) {
                    case APPLICATION_SUBMITTED -> "Application submitted via GCET Placement Hub";
                    case APPLICATION_REVIEWED -> "Profile and eligibility verified by Placement Cell";
                    case SHORTLISTED -> "Shortlisted for " + d.getCompanyName() + " assessment";
                    case ONLINE_ASSESSMENT -> "Cleared online assessment";
                    case TECHNICAL_INTERVIEW -> "Technical interview completed";
                    case HR_INTERVIEW -> "HR discussion completed";
                    case SELECTED -> "Selected by panel";
                    case OFFERED -> "Offer letter released (" + (offer != null ? offer : d.getSalaryPackageLpa()) + " LPA)";
                    case JOINED -> "Joined " + d.getCompanyName();
                    default -> "Moved to " + st.getLabel();
                };
                history.add(stageDoc(st, actor, remark, day));
                day -= 3;
                if (st == finalStage) break;
            }
        }

        Date appliedAt = daysAgo(startDaysAgo);
        Document doc = new Document("_id", new ObjectId())
                .append("studentId", toKey(s.getId()))
                .append("driveId", toKey(d.getId()))
                .append("companyId", toKey(d.getCompanyId()))
                .append("companyName", d.getCompanyName())
                .append("jobRole", d.getJobRole())
                .append("studentSnapshot", new Document("rollNumber", s.getRollNumber())
                        .append("fullName", s.getFullName())
                        .append("branch", s.getBranch())
                        .append("cgpa", s.getCgpa()))
                .append("currentStage", finalStage.name())
                .append("appliedAt", appliedAt)
                .append("finalOfferLpa", finalStage.isOfferOrSelected() ? (offer != null ? offer : d.getSalaryPackageLpa()) : null)
                .append("rejectionReason", rejection)
                .append("stageHistory", history);
        return doc;
    }

    private static Document stageDoc(ApplicationStage stage, String by, String remarks, int daysAgo) {
        return new Document("stage", stage.name())
                .append("updatedBy", by != null ? by : "Placement Cell")
                .append("timestamp", daysAgo(Math.max(0, daysAgo)))
                .append("remarks", remarks != null ? remarks : "");
    }

    // ------------------------------------------------------ approvals/requests

    private static void seedApprovalsAndRequests(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        loadExistingIds(db, ctx);
        String adminId = ctx.userIdByUsername.get("placement.admin");
        String principalId = ctx.userIdByUsername.get("principal.sagar");

        if (isEmpty(db, ApprovalDao.APPROVALS)) {
            List<Document> docs = new ArrayList<>();
            int i = 0;
            for (Student s : ctx.students) {
                if (!"APPROVED".equalsIgnoreCase(s.getApprovalStatus()) || s.getApprovedByFacultyId() == null) continue;
                i++;
                docs.add(new Document("requestType", "STUDENT_REGISTRATION")
                        .append("targetId", toKey(s.getId()))
                        .append("targetName", s.getFullName())
                        .append("studentRollNumber", s.getRollNumber())
                        .append("department", s.getBranch())
                        .append("approverId", toKey(s.getApprovedByFacultyId()))
                        .append("approverName", s.getApprovedByFacultyName())
                        .append("approverRole", "FACULTY")
                        .append("decision", "APPROVED")
                        .append("comments", "Verified departmental academic credentials & activated student portal login.")
                        .append("decidedAt", daysAgo(290 - i * 2)));
            }
            int c = 0;
            for (Map.Entry<String, String> e : ctx.companyIdByName.entrySet()) {
                c++;
                docs.add(new Document("requestType", "COMPANY_REGISTRATION")
                        .append("targetId", toKey(e.getValue()))
                        .append("targetName", e.getKey())
                        .append("studentRollNumber", null)
                        .append("department", "Corporate Relations")
                        .append("approverId", toKey(adminId))
                        .append("approverName", "Placement Administration Office")
                        .append("approverRole", "ADMIN")
                        .append("decision", "APPROVED")
                        .append("comments", "Corporate partner legitimacy verified; campus placement drives enabled.")
                        .append("decidedAt", daysAgo(118 - c * 3)));
            }
            for (String fu : List.of("faculty_lavanya", "faculty_aparna", "faculty_rajashakar")) {
                docs.add(new Document("requestType", "FACULTY_REGISTRATION")
                        .append("targetId", toKey(ctx.userIdByUsername.get(fu)))
                        .append("targetName", ctx.userNameByUsername.get(fu))
                        .append("studentRollNumber", null)
                        .append("department", "Faculty Coordination")
                        .append("approverId", toKey(adminId))
                        .append("approverName", "Placement Administration Office")
                        .append("approverRole", "ADMIN")
                        .append("decision", "APPROVED")
                        .append("comments", "Authorized faculty coordinator access & departmental clearance privileges.")
                        .append("decidedAt", daysAgo(200)));
            }
            if (!docs.isEmpty()) {
                db.getCollection(ApprovalDao.APPROVALS).insertMany(docs);
                summary.append(docs.size()).append(" approval records, ");
            }
        }

        if (isEmpty(db, ApprovalDao.ADMIN_REQUESTS)) {
            List<Document> docs = new ArrayList<>();
            docs.add(new Document("userId", toKey(ctx.userIdByUsername.get("tpo.gcet")))
                    .append("applicantName", ctx.userNameByUsername.get("tpo.gcet"))
                    .append("applicantEmail", "tpo@gcet.edu.in")
                    .append("currentRole", "PLACEMENT_OFFICER")
                    .append("requestedRole", "ADMIN")
                    .append("department", "Training & Placement Cell")
                    .append("reason", "Need administrator rights to approve recruiter registrations during the Feb 2026 mega drive week.")
                    .append("status", "PENDING")
                    .append("createdAt", daysAgo(1)));
            docs.add(new Document("userId", toKey(ctx.userIdByUsername.get("faculty_lavanya")))
                    .append("applicantName", ctx.userNameByUsername.get("faculty_lavanya"))
                    .append("applicantEmail", "lavanya.b@gcet.edu.in")
                    .append("currentRole", "FACULTY")
                    .append("requestedRole", "PLACEMENT_OFFICER")
                    .append("department", "CSE")
                    .append("reason", "Temporary placement officer access while the TPO is on leave.")
                    .append("status", "REJECTED")
                    .append("decidedBy", toKey(principalId))
                    .append("decidedByName", ctx.userNameByUsername.get("principal.sagar"))
                    .append("decisionNotes", "Not required - TPO delegated approvals manually for that week.")
                    .append("createdAt", daysAgo(50))
                    .append("decidedAt", daysAgo(48)));
            db.getCollection(ApprovalDao.ADMIN_REQUESTS).insertMany(docs);
            summary.append(docs.size()).append(" admin requests, ");
        }
    }

    // ----------------------------------------------------------- announcements

    private static void seedAnnouncements(MongoDatabase db, StringBuilder summary) {
        if (!isEmpty(db, AnnouncementDao.COLLECTION)) return;
        List<Document> docs = new ArrayList<>();
        docs.add(AnnouncementDao.toDocument(new GctcAnnouncement(null,
                "Placement Season 2025-26: Mega Drive Week Schedule Released",
                "10 Nov 2025", "PLACEMENT",
                "TCS, Infosys, Wipro and Capgemini will conduct campus drives between 24 and 28 November 2025. Eligible students must apply through the Placement Hub before the respective deadlines.",
                "https://gcet.edu.in/placements", true, "Mega_Drive_Week_Schedule_Nov25.pdf", "Training & Placement Cell"), daysAgo(5)));
        docs.add(AnnouncementDao.toDocument(new GctcAnnouncement(null,
                "Pre-Placement Aptitude & Communication Bootcamp (All Branches)",
                "02 Nov 2025", "TRAINING",
                "A 10-day bootcamp on quantitative aptitude, logical reasoning and HR interview skills starts on 12 November in Seminar Hall 2. Attendance is mandatory for final-year students.",
                "https://gcet.edu.in/placements", false, null, "Training & Placement Cell"), daysAgo(13)));
        docs.add(AnnouncementDao.toDocument(new GctcAnnouncement(null,
                "B.Tech, MBA & M.Tech Autonomous Revaluation Examination Results Published",
                "28 Oct 2024", "EXAMINATION",
                "Autonomous examination revaluation outcomes for B.Tech II & III Year (AR20, AR22 Regulations) and PG programs have been finalized and uploaded to the GCTC examination database.",
                "https://www.gctcportal.in/", true, "Revaluation_Results_Notification_Oct24.pdf", "Controller of Examinations, GCET"), daysAgo(380)));
        docs.add(AnnouncementDao.toDocument(new GctcAnnouncement(null,
                "IV B.Tech I Semester Mid-I Examination Timetables & CBT Schedules Released",
                "24 Oct 2024", "EXAMINATION",
                "Detailed session schedules, room allotments, and Computer Based Test (CBT) slot timings for IV B.Tech I Semester students across all branches are now active.",
                "https://www.gctcportal.in/", true, "IV_BTech_Mid1_Timetable_Schedule.pdf", "Academic & Examination Section"), daysAgo(384)));
        docs.add(AnnouncementDao.toDocument(new GctcAnnouncement(null,
                "Autonomous Regular & Supplementary Examination Fee Circular (AR20, AR22, AR24)",
                "18 Oct 2024", "EXAMINATION",
                "Notification regarding payment of examination fees for upcoming Autonomous Odd Semester End Examinations. Online payment enabled via State Bank Collect gateway.",
                "https://www.gctcportal.in/", false, "Exam_Fee_Circular_Autonomous_2024.pdf", "Office of the Principal"), daysAgo(390)));
        db.getCollection(AnnouncementDao.COLLECTION).insertMany(docs);
        summary.append(docs.size()).append(" announcements, ");
    }

    // -------------------------------------------------------------- audit logs

    private static void seedAuditLogs(MongoDatabase db, SeedContext ctx, StringBuilder summary) {
        if (!isEmpty(db, AuditLogDao.COLLECTION)) return;
        loadExistingIds(db, ctx);
        List<Document> docs = new ArrayList<>();
        String admin = ctx.userIdByUsername.get("placement.admin");
        String tpo = ctx.userIdByUsername.get("tpo.gcet");
        String lav = ctx.userIdByUsername.get("faculty_lavanya");
        String principal = ctx.userIdByUsername.get("principal.sagar");

        docs.add(AuditLogDao.buildLog("SEED_COMPLETED", admin, "placement.admin", "ADMIN",
                "Idempotent MongoDB seed populated demo collections (" + DatabaseConfig.getInstance().getClusterType() + ").", new Date()));
        int i = 0;
        for (PlacementDrive d : ctx.drives) {
            i++;
            docs.add(AuditLogDao.buildLog("DRIVE_CREATED", tpo, "tpo.gcet", "PLACEMENT_OFFICER",
                    "Created placement drive: " + d.getCompanyName() + " - " + d.getJobRole() + ".", daysAgo(70 - i * 4)));
        }
        for (Document app : ctx.applicationDocs) {
            String stage = str(app, "currentStage");
            if (stage == null) continue;
            if (ApplicationStage.fromString(stage).isOfferOrSelected()) {
                Document snap = sub(app, "studentSnapshot");
                docs.add(AuditLogDao.buildLog("APPLICATION_STAGE_UPDATED", tpo, "tpo.gcet", "PLACEMENT_OFFICER",
                        "Application " + id(app) + " (" + str(snap, "fullName") + " -> " + str(app, "companyName") + ") moved to " + stage + ".",
                        daysAgo(10 + (i++ % 20))));
            }
        }
        docs.add(AuditLogDao.buildLog("STUDENT_REGISTRATION_APPROVED", lav, "faculty_lavanya", "FACULTY",
                "Approved student Akhil Reddy Gaddam (22R11A0501, CSE).", daysAgo(288)));
        docs.add(AuditLogDao.buildLog("COMPANY_APPROVED", admin, "placement.admin", "ADMIN",
                "Approved corporate partner \"Amazon\" (Product / E-commerce & Cloud).", daysAgo(100)));
        docs.add(AuditLogDao.buildLog("ADMIN_PRIVILEGE_DECLINED", principal, "principal.sagar", "PRINCIPAL",
                "Principal declined PLACEMENT_OFFICER privilege for B. Laxmi Lavanya. Notes: Not required - TPO delegated approvals manually for that week.", daysAgo(48)));
        docs.add(AuditLogDao.buildLog("USER_LOGIN", principal, "principal.sagar", "PRINCIPAL",
                "Authenticated via Java Swing desktop client (ADMIN)", daysAgo(1)));
        docs.add(AuditLogDao.buildLog("USER_LOGIN", tpo, "tpo.gcet", "PLACEMENT_OFFICER",
                "Authenticated via Java Swing desktop client (FACULTY)", daysAgo(0)));
        db.getCollection(AuditLogDao.COLLECTION).insertMany(docs);
        summary.append(docs.size()).append(" audit logs");
    }
}
