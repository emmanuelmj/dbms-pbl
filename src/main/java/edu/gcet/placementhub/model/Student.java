package edu.gcet.placementhub.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * GCET PLACEMENT HUB - Student Model
 * Represents engineering students across GCET branches with academic
 * metrics, skill levels (1-5), and recruitment status.
 */
public class Student {
    private String id;
    private String rollNumber;
    private String studentIdCard;
    private String fullName;
    private String email;
    private String phone;
    private String branch;
    private int yearOfStudy = 1;
    private String section = "A";
    private double cgpa;
    private double tenthPercentage;
    private double interPercentage;
    private int graduationYear;
    private int backlogs;
    private String placementStatus; // UNPLACED, SHORTLISTED, PLACED, OPTED_OUT
    private String approvalStatus = "ACTIVE"; // PENDING, ACTIVE, REJECTED
    private String approvedByFacultyId;
    private String approvedByFacultyName;
    private Timestamp approvedAt;
    private String rejectionReason;
    private String userId;
    private String placedCompanyId;
    private Double placedPackageLpa;
    private String resumeUrl;
    private Timestamp createdAt;
    private int academicYear = 4;
    private int currentSemester = 7;
    private String dob;
    private String gender;
    private String address;
    private String location;
    private String aboutMe;
    private String technicalSkills;
    private String programmingLanguages;
    private String frameworksTechnologies;
    private String softSkills;
    private String careerInterests;
    private String preferredJobRoles;

    // Associated skills and applications
    private List<StudentSkill> skills = new ArrayList<>();

    public Student() {
        this.placementStatus = "UNPLACED";
        this.approvalStatus = "ACTIVE";
        this.backlogs = 0;
    }

    public Student(String id, String rollNumber, String fullName, String email, String branch, double cgpa, int graduationYear) {
        this();
        this.id = id;
        this.rollNumber = rollNumber;
        this.fullName = fullName;
        this.email = email;
        this.branch = branch;
        this.cgpa = cgpa;
        this.graduationYear = graduationYear;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

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

    public String getPlacedCompanyId() { return placedCompanyId; }
    public void setPlacedCompanyId(String placedCompanyId) { this.placedCompanyId = placedCompanyId; }

    public Double getPlacedPackageLpa() { return placedPackageLpa; }
    public void setPlacedPackageLpa(Double placedPackageLpa) { this.placedPackageLpa = placedPackageLpa; }

    public String getResumeUrl() { return resumeUrl; }
    public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public List<StudentSkill> getSkills() { return skills; }
    public void setSkills(List<StudentSkill> skills) { this.skills = skills; }

    public String getStudentIdCard() { return studentIdCard; }
    public void setStudentIdCard(String studentIdCard) { this.studentIdCard = studentIdCard; }

    public int getYearOfStudy() { return yearOfStudy; }
    public void setYearOfStudy(int yearOfStudy) { this.yearOfStudy = yearOfStudy; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public boolean isApproved() { return "ACTIVE".equalsIgnoreCase(approvalStatus); }

    public String getApprovedByFacultyId() { return approvedByFacultyId; }
    public void setApprovedByFacultyId(String approvedByFacultyId) { this.approvedByFacultyId = approvedByFacultyId; }

    public String getApprovedByFacultyName() { return approvedByFacultyName; }
    public void setApprovedByFacultyName(String approvedByFacultyName) { this.approvedByFacultyName = approvedByFacultyName; }

    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp approvedAt) { this.approvedAt = approvedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getAcademicYear() { return academicYear; }
    public void setAcademicYear(int academicYear) { this.academicYear = academicYear; }

    public int getCurrentSemester() { return currentSemester; }
    public void setCurrentSemester(int currentSemester) { this.currentSemester = currentSemester; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAboutMe() { return aboutMe; }
    public void setAboutMe(String aboutMe) { this.aboutMe = aboutMe; }

    public String getTechnicalSkills() { return technicalSkills; }
    public void setTechnicalSkills(String technicalSkills) { this.technicalSkills = technicalSkills; }

    public String getProgrammingLanguages() { return programmingLanguages; }
    public void setProgrammingLanguages(String programmingLanguages) { this.programmingLanguages = programmingLanguages; }

    public String getFrameworksTechnologies() { return frameworksTechnologies; }
    public void setFrameworksTechnologies(String frameworksTechnologies) { this.frameworksTechnologies = frameworksTechnologies; }

    public String getSoftSkills() { return softSkills; }
    public void setSoftSkills(String softSkills) { this.softSkills = softSkills; }

    public String getCareerInterests() { return careerInterests; }
    public void setCareerInterests(String careerInterests) { this.careerInterests = careerInterests; }

    public String getPreferredJobRoles() { return preferredJobRoles; }
    public void setPreferredJobRoles(String preferredJobRoles) { this.preferredJobRoles = preferredJobRoles; }

    @Override
    public String toString() {
        return fullName + " (" + rollNumber + " - " + branch + ")";
    }
}
