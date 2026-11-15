package edu.gcet.placementhub.service;

import edu.gcet.placementhub.dao.SkillDao;
import edu.gcet.placementhub.model.Skill;
import edu.gcet.placementhub.model.StudentSkill;
import edu.gcet.placementhub.model.User;

import java.util.*;

/**
 * GCET PLACEMENT HUB - Skill Matrix & Gap Analysis Service
 * Implements business validation, skill verification, matrix generation,
 * and exact mathematical skill gap analysis.
 */
public class SkillService {
    private final SkillDao skillDao;

    public SkillService() {
        this.skillDao = new SkillDao();
    }

    public SkillService(SkillDao skillDao) {
        this.skillDao = skillDao;
    }

    // ========================================================
    // 1. Skill Library
    // ========================================================

    public List<Skill> getSkills(String search, String category, String status) {
        return skillDao.findAllSkills(search, category, status);
    }

    public Skill getSkillById(String id) {
        return skillDao.findSkillById(id);
    }

    public void addSkill(User currentUser, Skill skill) throws StudentService.ValidationException {
        if (!currentUser.canManageSkillLibrary()) {
            throw new SecurityException("Permission Denied: Only System Administrators can add new skills to the institutional library.");
        }

        validateSkill(skill, null);

        boolean success = skillDao.insertSkill(skill);
        if (!success) {
            throw new RuntimeException("Database error occurred while adding skill to library.");
        }
    }

    public void updateSkill(User currentUser, Skill skill) throws StudentService.ValidationException {
        if (!currentUser.canManageSkillLibrary()) {
            throw new SecurityException("Permission Denied: Only System Administrators can modify skills in the institutional library.");
        }

        validateSkill(skill, skill.getId());

        boolean success = skillDao.updateSkill(skill);
        if (!success) {
            throw new RuntimeException("Database error occurred while updating skill.");
        }
    }

    public void toggleSkillStatus(User currentUser, String id, String status) {
        if (!currentUser.canManageSkillLibrary()) {
            throw new SecurityException("Permission Denied: Only System Administrators can toggle skill library status.");
        }

        boolean success = skillDao.toggleSkillStatus(id, status);
        if (!success) {
            throw new RuntimeException("Database error occurred while toggling skill status.");
        }
    }

    public void deleteSkill(User currentUser, String id) {
        if (!currentUser.canManageSkillLibrary()) {
            throw new SecurityException("Permission Denied: Only System Administrators can delete skills.");
        }

        boolean success = skillDao.deleteSkill(id);
        if (!success) {
            throw new RuntimeException("Database error occurred while deleting skill.");
        }
    }

    public List<String> getCategories() {
        return skillDao.getSkillCategories();
    }

    // ========================================================
    // 2. Student Skill Endorsements & Assessments
    // ========================================================

    public List<StudentSkill> getStudentSkills(String studentId) {
        return skillDao.findSkillsByStudentId(studentId);
    }

    public void saveStudentSkill(User currentUser, StudentSkill ss) throws StudentService.ValidationException {
        if (ss == null) {
            throw new StudentService.ValidationException("Student skill entry cannot be null.");
        }

        if (ss.getProficiency() < 1 || ss.getProficiency() > 5) {
            throw new StudentService.ValidationException("Proficiency rating must be between 1 (Beginner) and 4 (Expert).");
        }

        // If faculty verifies directly
        if (currentUser.canAssessSkills()) {
            ss.setVerifiedByFaculty(true);
            ss.setVerifiedByFacultyId(currentUser.getId());
            ss.setVerifiedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        }

        boolean success = skillDao.addOrUpdateStudentSkill(ss);
        if (!success) {
            throw new RuntimeException("Database error occurred while saving student skill rating.");
        }
    }

    public void removeStudentSkill(User currentUser, String studentId, String skillId) {
        if (!currentUser.canAssessSkills() && (currentUser.getStudentRollNumber() == null)) {
            throw new SecurityException("Permission Denied: Unauthorized skill modification.");
        }

        boolean success = skillDao.removeStudentSkill(studentId, skillId);
        if (!success) {
            throw new RuntimeException("Database error occurred while removing student skill.");
        }
    }

    public void verifyStudentSkill(User currentUser, String studentId, String skillId) {
        if (!currentUser.canAssessSkills()) {
            throw new SecurityException("Permission Denied: Only Faculty and Placement Officers can verify technical skills.");
        }

        boolean success = skillDao.verifyStudentSkill(studentId, skillId, currentUser.getId());
        if (!success) {
            throw new RuntimeException("Database error occurred while verifying student skill.");
        }
    }

    // ========================================================
    // 3. Exact Mathematical Skill Gap Analysis
    // ========================================================

    public static class SkillRequirement {
        private final String skillName;
        private final int minProficiency;

        public SkillRequirement(String skillName, int minProficiency) {
            this.skillName = skillName;
            this.minProficiency = minProficiency;
        }

        public String getSkillName() { return skillName; }
        public int getMinProficiency() { return minProficiency; }
    }

    public static class SkillGapReport {
        private final String studentId;
        private final List<SkillMatchItem> matchingSkills = new ArrayList<>();
        private final List<SkillGapItem> improvementSkills = new ArrayList<>();
        private final List<SkillRequirement> missingSkills = new ArrayList<>();
        private double readinessPercentage = 0.0;
        private int totalRequired = 0;
        private int fullyMatched = 0;

        public SkillGapReport(String studentId) {
            this.studentId = studentId;
        }

        public String getStudentId() { return studentId; }
        public List<SkillMatchItem> getMatchingSkills() { return matchingSkills; }
        public List<SkillGapItem> getImprovementSkills() { return improvementSkills; }
        public List<SkillRequirement> getMissingSkills() { return missingSkills; }
        public double getReadinessPercentage() { return readinessPercentage; }
        public int getTotalRequired() { return totalRequired; }
        public int getFullyMatched() { return fullyMatched; }
    }

    public static class SkillMatchItem {
        private final String skillName;
        private final int actualProficiency;
        private final int requiredProficiency;
        private final boolean isVerified;

        public SkillMatchItem(String skillName, int actualProficiency, int requiredProficiency, boolean isVerified) {
            this.skillName = skillName;
            this.actualProficiency = actualProficiency;
            this.requiredProficiency = requiredProficiency;
            this.isVerified = isVerified;
        }

        public String getSkillName() { return skillName; }
        public int getActualProficiency() { return actualProficiency; }
        public int getRequiredProficiency() { return requiredProficiency; }
        public boolean isVerified() { return isVerified; }
    }

    public static class SkillGapItem {
        private final String skillName;
        private final int actualProficiency;
        private final int requiredProficiency;
        private final int gap;

        public SkillGapItem(String skillName, int actualProficiency, int requiredProficiency) {
            this.skillName = skillName;
            this.actualProficiency = actualProficiency;
            this.requiredProficiency = requiredProficiency;
            this.gap = requiredProficiency - actualProficiency;
        }

        public String getSkillName() { return skillName; }
        public int getActualProficiency() { return actualProficiency; }
        public int getRequiredProficiency() { return requiredProficiency; }
        public int getGap() { return gap; }
    }

    /**
     * Exact Skill Gap Calculation:
     * Evaluates actual student skills against drive requirements without fake numbers.
     */
    public SkillGapReport calculateSkillGap(String studentId, List<SkillRequirement> requirements) {
        SkillGapReport report = new SkillGapReport(studentId);
        if (requirements == null || requirements.isEmpty()) {
            return report;
        }

        List<StudentSkill> studentSkills = getStudentSkills(studentId);
        Map<String, StudentSkill> studentSkillMap = new HashMap<>();
        for (StudentSkill ss : studentSkills) {
            if (ss.getSkillName() != null) {
                studentSkillMap.put(ss.getSkillName().trim().toLowerCase(), ss);
            }
        }

        int totalRequired = requirements.size();
        int matched = 0;
        double earnedPoints = 0.0;
        double maxPoints = 0.0;

        for (SkillRequirement req : requirements) {
            String key = req.getSkillName().trim().toLowerCase();
            int reqLevel = req.getMinProficiency();
            maxPoints += reqLevel;

            if (studentSkillMap.containsKey(key)) {
                StudentSkill actual = studentSkillMap.get(key);
                int actLevel = actual.getProficiency();

                if (actLevel >= reqLevel) {
                    matched++;
                    earnedPoints += reqLevel;
                    report.getMatchingSkills().add(new SkillMatchItem(
                        req.getSkillName(), actLevel, reqLevel, actual.isVerifiedByFaculty()
                    ));
                } else {
                    earnedPoints += actLevel;
                    report.getImprovementSkills().add(new SkillGapItem(
                        req.getSkillName(), actLevel, reqLevel
                    ));
                }
            } else {
                report.getMissingSkills().add(req);
            }
        }

        report.totalRequired = totalRequired;
        report.fullyMatched = matched;
        report.readinessPercentage = maxPoints > 0 ? Math.round((earnedPoints / maxPoints) * 1000.0) / 10.0 : 0.0;

        return report;
    }

    private void validateSkill(Skill s, String excludeId) throws StudentService.ValidationException {
        if (s == null) {
            throw new StudentService.ValidationException("Skill payload cannot be empty.");
        }

        if (s.getName() == null || s.getName().trim().length() < 2) {
            throw new StudentService.ValidationException("Skill name is required (minimum 2 characters).");
        }
        String name = s.getName().trim();
        if (skillDao.isSkillNameTaken(name, excludeId)) {
            throw new StudentService.ValidationException("Skill '" + name + "' already exists in the institutional catalog.");
        }
        s.setName(name);

        if (s.getCategory() == null || s.getCategory().trim().isEmpty()) {
            throw new StudentService.ValidationException("Skill category is required.");
        }
        s.setCategory(s.getCategory().trim());

        if (s.getStatus() == null || s.getStatus().trim().isEmpty()) {
            s.setStatus("ACTIVE");
        }
    }
}
