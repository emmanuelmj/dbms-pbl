package edu.gcet.placementhub.models;

/**
 * Student domain entity mapped from MySQL `students` table.
 */
public class Student {
    private String id;
    private String rollNumber;
    private String studentIdCard;
    private String fullName;
    private String email;
    private String phone;
    private String branch;
    private int yearOfStudy;
    private int academicYear;
    private String section;
    private double cgpa;
    private double tenthPercentage;
    private double interPercentage;
    private int graduationYear;
    private int backlogs;
    private String placementStatus;
    private String approvalStatus;
    private String approvedByFacultyId;
    private String approvedByFacultyName;
    private String approvedAt;
    private String rejectionReason;
    private String userId;
    private String placedCompanyId;
    private Double placedPackageLpa;
    private String resumeUrl;
    private String createdAt;
    private java.util.List<StudentSkill> skills = new java.util.ArrayList<>();
    private java.util.List<StudentProject> projects = new java.util.ArrayList<>();
    private java.util.List<StudentCertification> certifications = new java.util.ArrayList<>();
    private java.util.List<StudentInternship> internships = new java.util.ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public String getStudentIdCard() { return studentIdCard; }
    public void setStudentIdCard(String studentIdCard) { this.studentIdCard = studentIdCard; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public int getYearOfStudy() { return yearOfStudy; }
    public void setYearOfStudy(int yearOfStudy) { this.yearOfStudy = yearOfStudy; }

    public int getAcademicYear() { return academicYear; }
    public void setAcademicYear(int academicYear) { this.academicYear = academicYear; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public double getCgpa() { return cgpa; }
    public void setCgpa(double cgpa) { this.cgpa = cgpa; }

    public double getTenthPercentage() { return tenthPercentage; }
    public void setTenthPercentage(double tenthPercentage) { this.tenthPercentage = tenthPercentage; }

    public double getInterPercentage() { return interPercentage; }
    public void setInterPercentage(double interPercentage) { this.interPercentage = interPercentage; }

    public int getGraduationYear() { return graduationYear; }
    public void setGraduationYear(int graduationYear) { this.graduationYear = graduationYear; }

    public int getBacklogs() { return backlogs; }
    public void setBacklogs(int backlogs) { this.backlogs = backlogs; }

    public String getPlacementStatus() { return placementStatus; }
    public void setPlacementStatus(String placementStatus) { this.placementStatus = placementStatus; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getApprovedByFacultyId() { return approvedByFacultyId; }
    public void setApprovedByFacultyId(String approvedByFacultyId) { this.approvedByFacultyId = approvedByFacultyId; }

    public String getApprovedByFacultyName() { return approvedByFacultyName; }
    public void setApprovedByFacultyName(String approvedByFacultyName) { this.approvedByFacultyName = approvedByFacultyName; }

    public String getApprovedAt() { return approvedAt; }
    public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPlacedCompanyId() { return placedCompanyId; }
    public void setPlacedCompanyId(String placedCompanyId) { this.placedCompanyId = placedCompanyId; }

    public Double getPlacedPackageLpa() { return placedPackageLpa; }
    public void setPlacedPackageLpa(Double placedPackageLpa) { this.placedPackageLpa = placedPackageLpa; }

    public String getResumeUrl() { return resumeUrl; }
    public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public java.util.List<StudentSkill> getSkills() { return skills; }
    public void setSkills(java.util.List<StudentSkill> skills) {
        this.skills = skills != null ? skills : new java.util.ArrayList<>();
    }

    public java.util.List<StudentProject> getProjects() { return projects; }
    public void setProjects(java.util.List<StudentProject> projects) {
        this.projects = projects != null ? projects : new java.util.ArrayList<>();
    }

    public java.util.List<StudentCertification> getCertifications() { return certifications; }
    public void setCertifications(java.util.List<StudentCertification> certifications) {
        this.certifications = certifications != null ? certifications : new java.util.ArrayList<>();
    }

    public java.util.List<StudentInternship> getInternships() { return internships; }
    public void setInternships(java.util.List<StudentInternship> internships) {
        this.internships = internships != null ? internships : new java.util.ArrayList<>();
    }
}
