package edu.gcet.placementhub.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified role-aware profile aggregate combining user account, profile metadata,
 * social links, and role-specific sub-records (Student / Faculty / Company / Admin).
 */
public class UserProfileData {
    private User user;
    private Student student;
    private Faculty faculty;
    private Company company;

    private String profilePhoto;
    private String phone;
    private String designation;
    private String location;
    private String address;
    private String aboutMe;
    private String dateOfBirth;
    private String gender;

    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private String leetcodeUrl;
    private String hackerrankUrl;
    private String codechefUrl;
    private String gfgUrl;
    private String kaggleUrl;
    private String googleDevUrl;

    private List<CustomLink> customLinks = new ArrayList<>();
    private List<StudentProject> projects = new ArrayList<>();
    private List<StudentInternship> internships = new ArrayList<>();
    private List<UserCertification> certifications = new ArrayList<>();
    private List<StudentAchievement> achievements = new ArrayList<>();
    private List<FacultyPublication> publications = new ArrayList<>();

    private int completionPercentage;
    private List<String> missingSections = new ArrayList<>();

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Faculty getFaculty() { return faculty; }
    public void setFaculty(Faculty faculty) { this.faculty = faculty; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getProfilePhoto() { return profilePhoto; }
    public void setProfilePhoto(String profilePhoto) { this.profilePhoto = profilePhoto; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getAboutMe() { return aboutMe; }
    public void setAboutMe(String aboutMe) { this.aboutMe = aboutMe; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }

    public String getLeetcodeUrl() { return leetcodeUrl; }
    public void setLeetcodeUrl(String leetcodeUrl) { this.leetcodeUrl = leetcodeUrl; }

    public String getHackerrankUrl() { return hackerrankUrl; }
    public void setHackerrankUrl(String hackerrankUrl) { this.hackerrankUrl = hackerrankUrl; }

    public String getCodechefUrl() { return codechefUrl; }
    public void setCodechefUrl(String codechefUrl) { this.codechefUrl = codechefUrl; }

    public String getGfgUrl() { return gfgUrl; }
    public void setGfgUrl(String gfgUrl) { this.gfgUrl = gfgUrl; }

    public String getKaggleUrl() { return kaggleUrl; }
    public void setKaggleUrl(String kaggleUrl) { this.kaggleUrl = kaggleUrl; }

    public String getGoogleDevUrl() { return googleDevUrl; }
    public void setGoogleDevUrl(String googleDevUrl) { this.googleDevUrl = googleDevUrl; }

    public List<CustomLink> getCustomLinks() { return customLinks; }
    public void setCustomLinks(List<CustomLink> customLinks) { this.customLinks = customLinks; }

    public List<StudentProject> getProjects() { return projects; }
    public void setProjects(List<StudentProject> projects) { this.projects = projects; }

    public List<StudentInternship> getInternships() { return internships; }
    public void setInternships(List<StudentInternship> internships) { this.internships = internships; }

    public List<UserCertification> getCertifications() { return certifications; }
    public void setCertifications(List<UserCertification> certifications) { this.certifications = certifications; }

    public List<StudentAchievement> getAchievements() { return achievements; }
    public void setAchievements(List<StudentAchievement> achievements) { this.achievements = achievements; }

    public List<FacultyPublication> getPublications() { return publications; }
    public void setPublications(List<FacultyPublication> publications) { this.publications = publications; }

    public int getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(int completionPercentage) { this.completionPercentage = completionPercentage; }

    public List<String> getMissingSections() { return missingSections; }
    public void setMissingSections(List<String> missingSections) { this.missingSections = missingSections; }
}
