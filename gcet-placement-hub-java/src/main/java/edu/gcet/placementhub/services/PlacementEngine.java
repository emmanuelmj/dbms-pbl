package edu.gcet.placementhub.services;

import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.*;
import edu.gcet.placementhub.utils.DeptNormalizer;

import java.time.LocalDate;
import java.util.*;

/**
 * Deterministic Eligibility Checker, Skill Gap Analyzer, and Smart Company Matching Engine.
 * Faithfully ports eligibilityService.ts, skillGapService.ts, and matchingEngine.ts using real MySQL records.
 */
public final class PlacementEngine {

    private PlacementEngine() {
    }

    public static boolean isDriveOpenForApplications(PlacementDrive drive) {
        if (drive == null) return false;
        String st = drive.getStatus() != null ? drive.getStatus().trim().toUpperCase(Locale.ROOT) : "OPEN";
        return "OPEN".equals(st) || "UPCOMING".equals(st) || "PLANNED".equals(st);
    }

    public static boolean isDeadlineExpired(PlacementDrive drive) {
        if (drive == null || drive.getApplicationDeadline() == null || drive.getApplicationDeadline().isBlank()) {
            return false;
        }
        try {
            LocalDate deadline = LocalDate.parse(drive.getApplicationDeadline().trim().substring(0, 10));
            return deadline.isBefore(LocalDate.now());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static EligibilityResult checkEligibility(Student student, PlacementDrive drive, List<Skill> allSkills) {
        List<String> reasons = new ArrayList<>();
        if (student == null || drive == null) {
            reasons.add("Student profile or placement drive record is missing.");
            return new EligibilityResult(
                    false, 0,
                    false, 0, 0, "No data",
                    false, "", List.of(), "No data",
                    false, 0, 0, "No data",
                    false, 0, 0, "No data",
                    false, 0, 0, List.of(), "No data",
                    reasons
            );
        }

        // 1. CGPA Check
        boolean cgpaPassed = student.getCgpa() >= drive.getMinCgpa();
        if (!cgpaPassed) {
            reasons.add(String.format(Locale.US,
                    "CGPA %.2f is below minimum required CGPA of %.2f",
                    student.getCgpa(), drive.getMinCgpa()));
        }

        // 2. Branch Eligibility Check (exact or normalized GCET branch match)
        List<String> allowedBranches = drive.getEligibleBranches() != null ? drive.getEligibleBranches() : List.of();
        String normStudentBranch = DeptNormalizer.normalizeDeptCode(student.getBranch());
        boolean branchPassed = false;
        if (allowedBranches.isEmpty()) {
            branchPassed = true;
        } else {
            for (String b : allowedBranches) {
                if (b != null && (b.equalsIgnoreCase(student.getBranch())
                        || DeptNormalizer.normalizeDeptCode(b).equalsIgnoreCase(normStudentBranch))) {
                    branchPassed = true;
                    break;
                }
            }
        }
        if (!branchPassed) {
            reasons.add(String.format(
                    "Branch \"%s\" is not among eligible branches (%s)",
                    student.getBranch(), String.join(", ", allowedBranches)));
        }

        // 3. Graduation Year Check
        boolean gradYearPassed = drive.getTargetGraduationYear() <= 0
                || student.getGraduationYear() == drive.getTargetGraduationYear();
        if (!gradYearPassed) {
            reasons.add(String.format(
                    "Graduation year %d does not match required passing batch of %d",
                    student.getGraduationYear(), drive.getTargetGraduationYear()));
        }

        // 4. Backlog Check
        boolean backlogsPassed = student.getBacklogs() <= drive.getMaxBacklogsAllowed();
        if (!backlogsPassed) {
            reasons.add(String.format(
                    "Student has %d active backlog(s); drive strictly permits maximum of %d",
                    student.getBacklogs(), drive.getMaxBacklogsAllowed()));
        }

        // 5. Required Technical Skills & Proficiency Check
        Map<String, Skill> skillCatalog = new HashMap<>();
        if (allSkills != null) {
            for (Skill s : allSkills) {
                skillCatalog.put(s.getId(), s);
            }
        }
        Map<String, Integer> studentSkillMap = new HashMap<>();
        if (student.getSkills() != null) {
            for (StudentSkill ss : student.getSkills()) {
                studentSkillMap.put(ss.getSkillId(), ss.getProficiency());
            }
        }

        List<DriveRequiredSkill> reqSkills = drive.getRequiredSkills() != null ? drive.getRequiredSkills() : List.of();
        List<SkillCheckDetail> skillDetails = new ArrayList<>();
        int satisfiedSkillsCount = 0;
        boolean mandatorySkillsSatisfied = true;

        for (DriveRequiredSkill req : reqSkills) {
            Skill meta = skillCatalog.get(req.getSkillId());
            String skillName = meta != null ? meta.getName() : req.getSkillId();
            int studentProf = studentSkillMap.getOrDefault(req.getSkillId(), 0);
            int reqProf = Math.max(1, req.getMinProficiency());
            boolean satisfied = studentProf >= reqProf;

            if (satisfied) {
                satisfiedSkillsCount++;
            } else if (req.isMandatory()) {
                mandatorySkillsSatisfied = false;
                if (studentProf == 0) {
                    reasons.add(String.format("Missing mandatory skill: %s (Requires Level %d)", skillName, reqProf));
                } else {
                    reasons.add(String.format("Skill %s level %d is below required proficiency level %d",
                            skillName, studentProf, reqProf));
                }
            }

            skillDetails.add(new SkillCheckDetail(
                    req.getSkillId(),
                    skillName,
                    studentProf,
                    reqProf,
                    satisfied,
                    req.isMandatory(),
                    Math.max(0, reqProf - studentProf)
            ));
        }

        boolean eligible = cgpaPassed && branchPassed && gradYearPassed && backlogsPassed && mandatorySkillsSatisfied;

        // Quantitative preparedness score (0-100) using exact eligibilityService.ts weights
        double rawScore = 0;
        if (cgpaPassed) {
            rawScore += 30;
        } else if (drive.getMinCgpa() > 0) {
            rawScore += Math.max(0, (student.getCgpa() / drive.getMinCgpa()) * 20.0);
        }
        if (branchPassed) rawScore += 20;
        if (gradYearPassed) rawScore += 10;
        if (backlogsPassed) rawScore += 10;

        int totalSkills = reqSkills.size();
        if (totalSkills > 0) {
            double skillRatio = (double) satisfiedSkillsCount / totalSkills;
            rawScore += Math.round(skillRatio * 30.0);
        } else {
            rawScore += 30;
        }

        int finalScore = (int) Math.min(100, Math.max(0, Math.round(rawScore)));

        String cgpaMsg = cgpaPassed
                ? String.format(Locale.US, "CGPA %.2f meets criteria (>= %.2f)", student.getCgpa(), drive.getMinCgpa())
                : String.format(Locale.US, "CGPA %.2f is below minimum requirement (%.2f)", student.getCgpa(), drive.getMinCgpa());
        String branchMsg = branchPassed
                ? "Branch " + student.getBranch() + " is eligible"
                : "Branch " + student.getBranch() + " is not eligible for this role";
        String gradMsg = gradYearPassed
                ? "Graduation year " + student.getGraduationYear() + " matches"
                : "Graduation year must be " + drive.getTargetGraduationYear();
        String backlogMsg = backlogsPassed
                ? String.format("Backlogs (%d) within permissible limit (%d)", student.getBacklogs(), drive.getMaxBacklogsAllowed())
                : String.format("Exceeds maximum allowable backlogs (%d)", drive.getMaxBacklogsAllowed());
        String skillsMsg = mandatorySkillsSatisfied
                ? String.format("All mandatory skills satisfied (%d/%d total met)", satisfiedSkillsCount, totalSkills)
                : "Missing or insufficient proficiency in mandatory skills";

        return new EligibilityResult(
                eligible,
                finalScore,
                cgpaPassed, student.getCgpa(), drive.getMinCgpa(), cgpaMsg,
                branchPassed, student.getBranch(), allowedBranches, branchMsg,
                gradYearPassed, student.getGraduationYear(), drive.getTargetGraduationYear(), gradMsg,
                backlogsPassed, student.getBacklogs(), drive.getMaxBacklogsAllowed(), backlogMsg,
                mandatorySkillsSatisfied, totalSkills, satisfiedSkillsCount, skillDetails, skillsMsg,
                reasons
        );
    }

    public static SkillGapAnalysis analyzeSkillGap(Student student, PlacementDrive drive, List<Skill> allSkills) {
        Map<String, Skill> skillCatalog = new HashMap<>();
        if (allSkills != null) {
            for (Skill s : allSkills) {
                skillCatalog.put(s.getId(), s);
            }
        }
        Map<String, Integer> studentSkillMap = new HashMap<>();
        if (student != null && student.getSkills() != null) {
            for (StudentSkill ss : student.getSkills()) {
                studentSkillMap.put(ss.getSkillId(), ss.getProficiency());
            }
        }

        List<SkillGapItem> items = new ArrayList<>();
        int satisfiedCount = 0;
        int partialCount = 0;
        int missingCount = 0;

        List<DriveRequiredSkill> reqs = drive != null && drive.getRequiredSkills() != null
                ? drive.getRequiredSkills()
                : List.of();

        for (DriveRequiredSkill req : reqs) {
            Skill meta = skillCatalog.get(req.getSkillId());
            String skillName = meta != null ? meta.getName() : req.getSkillId();
            String category = meta != null && meta.getCategory() != null ? meta.getCategory() : "Technical Competency";
            int studentProf = studentSkillMap.getOrDefault(req.getSkillId(), 0);
            int reqProf = Math.max(1, req.getMinProficiency());

            SkillGapItem.Status status;
            int gapLevel = 0;
            if (studentProf >= reqProf) {
                status = SkillGapItem.Status.MET;
                satisfiedCount++;
            } else if (studentProf > 0) {
                status = SkillGapItem.Status.PARTIAL;
                partialCount++;
                gapLevel = reqProf - studentProf;
            } else {
                status = SkillGapItem.Status.MISSING;
                missingCount++;
                gapLevel = reqProf;
            }

            String recommendation = (status == SkillGapItem.Status.MET)
                    ? "Skill verified and ready for technical interview rounds."
                    : curriculumRecommendation(skillName, studentProf, reqProf);

            items.add(new SkillGapItem(
                    req.getSkillId(),
                    skillName,
                    category,
                    studentProf,
                    reqProf,
                    status,
                    gapLevel,
                    req.isMandatory(),
                    recommendation
            ));
        }

        int totalSkills = reqs.size();
        int readinessPercentage = 100;
        if (totalSkills > 0) {
            double totalPoints = satisfiedCount * 1.0 + partialCount * 0.5;
            readinessPercentage = (int) Math.round((totalPoints / totalSkills) * 100.0);
        }

        List<String> actionPlan = new ArrayList<>();
        List<String> recommendedSkills = new ArrayList<>();
        List<String> missingNames = new ArrayList<>();
        List<String> partialNames = new ArrayList<>();
        for (SkillGapItem item : items) {
            if (item.status == SkillGapItem.Status.MISSING) {
                missingNames.add(item.skillName);
                recommendedSkills.add(item.skillName + " (Target Level " + item.requiredProficiency + " - Missing)");
            } else if (item.status == SkillGapItem.Status.PARTIAL) {
                partialNames.add(item.skillName);
                recommendedSkills.add(item.skillName + " (Level " + item.studentProficiency + " -> Level " + item.requiredProficiency + ")");
            }
        }

        if (!missingNames.isEmpty()) {
            actionPlan.add("Urgent: Begin foundational modules for " + String.join(", ", missingNames) + ".");
        }
        if (!partialNames.isEmpty()) {
            actionPlan.add("Bridge Gap: Schedule faculty review or practice advanced problem sets for " + String.join(", ", partialNames) + ".");
        }
        if (items.isEmpty() || (missingNames.isEmpty() && partialNames.isEmpty())) {
            actionPlan.add("All required technical skills satisfied! Conduct mock technical interviews with GCET faculty panel.");
        } else {
            actionPlan.add("Leverage GCET departmental coding labs and peer review circles prior to the drive deadline.");
        }

        return new SkillGapAnalysis(
                student != null ? student.getId() : "",
                student != null ? student.getFullName() : "",
                drive != null ? drive.getId() : "",
                drive != null ? drive.getJobRole() : "",
                drive != null ? drive.getCompanyName() : "",
                readinessPercentage,
                totalSkills,
                satisfiedCount,
                partialCount,
                missingCount,
                items,
                actionPlan,
                recommendedSkills
        );
    }

    public static List<CompanyMatch> findMatchesForStudent(
            Student student,
            List<PlacementDrive> allDrives,
            List<Company> companies,
            List<Skill> allSkills
    ) {
        if (student == null || allDrives == null) return List.of();

        Map<String, Company> companyById = new HashMap<>();
        if (companies != null) {
            for (Company c : companies) {
                companyById.put(c.getId(), c);
            }
        }
        Map<String, Skill> skillCatalog = new HashMap<>();
        if (allSkills != null) {
            for (Skill s : allSkills) {
                skillCatalog.put(s.getId(), s);
            }
        }
        Map<String, Integer> studentSkillMap = new HashMap<>();
        if (student.getSkills() != null) {
            for (StudentSkill ss : student.getSkills()) {
                studentSkillMap.put(ss.getSkillId(), ss.getProficiency());
            }
        }

        List<CompanyMatch> matches = new ArrayList<>();
        for (PlacementDrive drive : allDrives) {
            String status = drive.getStatus() != null ? drive.getStatus().toUpperCase(Locale.ROOT) : "OPEN";
            if (!"OPEN".equals(status) && !"UPCOMING".equals(status)) {
                continue;
            }

            Company comp = companyById.get(drive.getCompanyId());
            if (comp == null) {
                comp = new Company();
                comp.setId(drive.getCompanyId());
                comp.setName(drive.getCompanyName());
                comp.setIndustry("Information Technology");
                comp.setLocation("Hyderabad, Telangana");
                comp.setTier("Standard IT");
            }

            EligibilityResult eligibility = checkEligibility(student, drive, allSkills);

            List<String> matchedSkills = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();
            if (drive.getRequiredSkills() != null) {
                for (DriveRequiredSkill req : drive.getRequiredSkills()) {
                    Skill meta = skillCatalog.get(req.getSkillId());
                    String name = meta != null ? meta.getName() : req.getSkillId();
                    int prof = studentSkillMap.getOrDefault(req.getSkillId(), 0);
                    if (prof >= Math.max(1, req.getMinProficiency())) {
                        matchedSkills.add(name);
                    } else {
                        missingSkills.add(name);
                    }
                }
            }

            String explanation;
            int totalReq = drive.getRequiredSkills() != null ? drive.getRequiredSkills().size() : 0;
            if (eligibility.eligible) {
                explanation = String.format(Locale.US,
                        "Eligible Candidate (%d%% match): Meets CGPA cutoff (%.2f >= %.2f), branch (%s) matches, and satisfied %d of %d required skills.",
                        eligibility.score, student.getCgpa(), drive.getMinCgpa(),
                        student.getBranch(), matchedSkills.size(), totalReq);
            } else {
                String primaryReason = !eligibility.reasons.isEmpty() ? eligibility.reasons.get(0) : "Unmet drive criteria";
                String bridgeNote = !missingSkills.isEmpty()
                        ? " Skills to bridge: " + String.join(", ", missingSkills.subList(0, Math.min(3, missingSkills.size()))) + "."
                        : "";
                explanation = "Ineligible for direct application: " + primaryReason + "." + bridgeNote;
            }

            matches.add(new CompanyMatch(
                    drive,
                    comp,
                    eligibility.score,
                    eligibility,
                    matchedSkills,
                    missingSkills,
                    explanation
            ));
        }

        matches.sort((a, b) -> {
            if (a.eligibility.eligible != b.eligibility.eligible) {
                return a.eligibility.eligible ? -1 : 1;
            }
            int scoreCmp = Integer.compare(b.matchScore, a.matchScore);
            if (scoreCmp != 0) return scoreCmp;
            return Double.compare(b.drive.getSalaryPackageLpa(), a.drive.getSalaryPackageLpa());
        });
        return matches;
    }

    private static String curriculumRecommendation(String skillName, int studentProf, int requiredProf) {
        String norm = skillName != null ? skillName.toLowerCase(Locale.ROOT) : "";
        if (studentProf <= 0) {
            if (norm.contains("java")) {
                return "Complete GCET Java Programming Lab: Focus on OOP principles, Polymorphism, Collections Framework, and Exception Handling.";
            }
            if (norm.contains("data structures") || norm.contains("dsa")) {
                return "Practice core DSA modules: Arrays, Linked Lists, Stacks, Queues, Binary Trees, and solve foundational coding sets.";
            }
            if (norm.contains("algorithm")) {
                return "Review algorithmic paradigms: Big-O analysis, Sorting, Binary Search, and Dynamic Programming foundations.";
            }
            if (norm.contains("mysql") || norm.contains("database") || norm.contains("sql")) {
                return "Study SQL fundamentals: INNER/LEFT JOINs, Subqueries, Normalization (1NF to 3NF), and MySQL transaction isolation.";
            }
            if (norm.contains("react")) {
                return "Build a portfolio project with React: JSX, component state hooks, props passing, and lifecycle management.";
            }
            if (norm.contains("spring boot")) {
                return "Learn Spring Initializr: Dependency Injection, REST controllers, and JDBC/JPA persistence with MySQL.";
            }
            if (norm.contains("linux")) {
                return "Learn standard Linux terminal commands: grep, awk, file permissions (chmod), piping, and process management.";
            }
            if (norm.contains("network")) {
                return "Review Computer Networks fundamentals: OSI 7-layer model, TCP 3-way handshake, HTTP/HTTPS, DNS, and routing.";
            }
            if (norm.contains("aws") || norm.contains("cloud")) {
                return "Review AWS core services: EC2 virtual machines, S3 bucket storage, and IAM security policies.";
            }
            if (norm.contains("aptitude")) {
                return "Practice GCET Placement Cell Aptitude modules: Quantitative Time & Work, Speed & Distance, and Logical Reasoning.";
            }
            return "Enroll in bridge training for " + skillName + " to acquire Level " + requiredProf + " (" + PlacementModels.proficiencyLabel(requiredProf) + ") proficiency.";
        }

        if (norm.contains("java")) {
            return "Advance to Java 17 Streams API, Lambdas, Concurrency (ExecutorService), and JDBC transaction patterns.";
        }
        if (norm.contains("data structures") || norm.contains("dsa")) {
            return "Level up to Graph algorithms (BFS/DFS, Dijkstra), Trie structures, and Disjoint Set Union (DSU).";
        }
        if (norm.contains("algorithm")) {
            return "Deep dive into 2D Dynamic Programming (Knapsack, LCS), Backtracking, and Greedy optimizations.";
        }
        if (norm.contains("mysql") || norm.contains("database") || norm.contains("sql")) {
            return "Master B-Tree indexing, EXPLAIN execution plan tuning, stored procedures, and deadlock prevention.";
        }
        if (norm.contains("spring boot")) {
            return "Implement JWT security filter chains, API gateways, and microservice resilience patterns.";
        }
        if (norm.contains("linux")) {
            return "Automate workflows with Shell scripting, systemd service management, and network diagnostic tools.";
        }
        return "Upskill in " + skillName + " from Level " + studentProf + " (" + PlacementModels.proficiencyLabel(studentProf)
                + ") to target Level " + requiredProf + " (" + PlacementModels.proficiencyLabel(requiredProf) + ").";
    }
}
