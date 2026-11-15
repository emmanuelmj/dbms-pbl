package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - StudentSkill Model
 * Maps student technical competencies on a 1-5 proficiency scale
 * with faculty verification status.
 */
public class StudentSkill {
    private String studentId;
    private String skillId;
    private String skillName;
    private String skillCategory;
    private int proficiency; // 1 to 4 (or 5)
    private String experienceDuration;
    private String certificationEvidence;
    private boolean verifiedByFaculty;
    private String verifiedByFacultyId;
    private String verifierName;
    private Timestamp verifiedAt;

    public StudentSkill() {
        this.experienceDuration = "6 months";
        this.certificationEvidence = "";
    }

    public StudentSkill(String studentId, String skillId, int proficiency) {
        this();
        this.studentId = studentId;
        this.skillId = skillId;
        this.proficiency = proficiency;
    }

    public static String getProficiencyLabel(int level) {
        switch (level) {
            case 1: return "Beginner";
            case 2: return "Intermediate";
            case 3: return "Advanced";
            case 4: return "Expert";
            case 5: return "Master";
            default: return "Level " + level;
        }
    }

    public String getProficiencyText() {
        return getProficiencyLabel(this.proficiency);
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }

    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }

    public String getSkillCategory() { return skillCategory; }
    public void setSkillCategory(String skillCategory) { this.skillCategory = skillCategory; }

    public int getProficiency() { return proficiency; }
    public void setProficiency(int proficiency) { this.proficiency = proficiency; }

    public String getExperienceDuration() { return experienceDuration; }
    public void setExperienceDuration(String experienceDuration) { this.experienceDuration = experienceDuration; }

    public String getCertificationEvidence() { return certificationEvidence; }
    public void setCertificationEvidence(String certificationEvidence) { this.certificationEvidence = certificationEvidence; }

    public boolean isVerifiedByFaculty() { return verifiedByFaculty; }
    public void setVerifiedByFaculty(boolean verifiedByFaculty) { this.verifiedByFaculty = verifiedByFaculty; }

    public String getVerifiedByFacultyId() { return verifiedByFacultyId; }
    public void setVerifiedByFacultyId(String verifiedByFacultyId) { this.verifiedByFacultyId = verifiedByFacultyId; }

    public String getVerifierName() { return verifierName; }
    public void setVerifierName(String verifierName) { this.verifierName = verifierName; }

    public Timestamp getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Timestamp verifiedAt) { this.verifiedAt = verifiedAt; }
}
