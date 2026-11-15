package edu.gcet.placementhub.models;

/**
 * Student competency record mapped from MySQL `student_skills` table.
 */
public class StudentSkill {
    private String studentId;
    private String skillId;
    private int proficiency;
    private boolean verifiedByFaculty;
    private String verifiedByFacultyId;
    private String verifiedAt;

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }

    public int getProficiency() { return proficiency; }
    public void setProficiency(int proficiency) { this.proficiency = proficiency; }

    public boolean isVerifiedByFaculty() { return verifiedByFaculty; }
    public void setVerifiedByFaculty(boolean verifiedByFaculty) { this.verifiedByFaculty = verifiedByFaculty; }

    public String getVerifiedByFacultyId() { return verifiedByFacultyId; }
    public void setVerifiedByFacultyId(String verifiedByFacultyId) { this.verifiedByFacultyId = verifiedByFacultyId; }

    public String getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(String verifiedAt) { this.verifiedAt = verifiedAt; }
}
