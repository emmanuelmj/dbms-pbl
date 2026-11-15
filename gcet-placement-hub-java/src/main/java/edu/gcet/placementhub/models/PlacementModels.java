package edu.gcet.placementhub.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 domain result DTOs for Eligibility Engine, Skill Gap Analysis,
 * Smart Company Matching, and unified Placement Management bundle loading.
 */
public final class PlacementModels {

    public static final Map<Integer, String> PROFICIENCY_LABELS = Map.of(
            1, "Beginner",
            2, "Elementary",
            3, "Intermediate",
            4, "Advanced",
            5, "Expert"
    );

    private PlacementModels() {
    }

    public static String proficiencyLabel(int level) {
        if (level <= 0) return "None";
        return PROFICIENCY_LABELS.getOrDefault(level, "Level " + level);
    }

    public static class SkillCheckDetail {
        public final String skillId;
        public final String skillName;
        public final int studentProficiency;
        public final int requiredProficiency;
        public final boolean satisfied;
        public final boolean mandatory;
        public final int gap;

        public SkillCheckDetail(
                String skillId,
                String skillName,
                int studentProficiency,
                int requiredProficiency,
                boolean satisfied,
                boolean mandatory,
                int gap
        ) {
            this.skillId = skillId;
            this.skillName = skillName;
            this.studentProficiency = studentProficiency;
            this.requiredProficiency = requiredProficiency;
            this.satisfied = satisfied;
            this.mandatory = mandatory;
            this.gap = gap;
        }
    }

    public static class EligibilityResult {
        public final boolean eligible;
        public final int score;

        public final boolean cgpaPassed;
        public final double studentCgpa;
        public final double requiredCgpa;
        public final String cgpaMessage;

        public final boolean branchPassed;
        public final String studentBranch;
        public final List<String> allowedBranches;
        public final String branchMessage;

        public final boolean gradYearPassed;
        public final int studentGradYear;
        public final int requiredGradYear;
        public final String gradYearMessage;

        public final boolean backlogsPassed;
        public final int studentBacklogs;
        public final int maxBacklogsAllowed;
        public final String backlogsMessage;

        public final boolean mandatorySkillsSatisfied;
        public final int totalRequiredSkills;
        public final int satisfiedSkillsCount;
        public final List<SkillCheckDetail> skillDetails;
        public final String skillsMessage;

        public final List<String> reasons;

        public EligibilityResult(
                boolean eligible,
                int score,
                boolean cgpaPassed,
                double studentCgpa,
                double requiredCgpa,
                String cgpaMessage,
                boolean branchPassed,
                String studentBranch,
                List<String> allowedBranches,
                String branchMessage,
                boolean gradYearPassed,
                int studentGradYear,
                int requiredGradYear,
                String gradYearMessage,
                boolean backlogsPassed,
                int studentBacklogs,
                int maxBacklogsAllowed,
                String backlogsMessage,
                boolean mandatorySkillsSatisfied,
                int totalRequiredSkills,
                int satisfiedSkillsCount,
                List<SkillCheckDetail> skillDetails,
                String skillsMessage,
                List<String> reasons
        ) {
            this.eligible = eligible;
            this.score = score;
            this.cgpaPassed = cgpaPassed;
            this.studentCgpa = studentCgpa;
            this.requiredCgpa = requiredCgpa;
            this.cgpaMessage = cgpaMessage;
            this.branchPassed = branchPassed;
            this.studentBranch = studentBranch;
            this.allowedBranches = allowedBranches != null ? allowedBranches : List.of();
            this.branchMessage = branchMessage;
            this.gradYearPassed = gradYearPassed;
            this.studentGradYear = studentGradYear;
            this.requiredGradYear = requiredGradYear;
            this.gradYearMessage = gradYearMessage;
            this.backlogsPassed = backlogsPassed;
            this.studentBacklogs = studentBacklogs;
            this.maxBacklogsAllowed = maxBacklogsAllowed;
            this.backlogsMessage = backlogsMessage;
            this.mandatorySkillsSatisfied = mandatorySkillsSatisfied;
            this.totalRequiredSkills = totalRequiredSkills;
            this.satisfiedSkillsCount = satisfiedSkillsCount;
            this.skillDetails = skillDetails != null ? skillDetails : List.of();
            this.skillsMessage = skillsMessage;
            this.reasons = reasons != null ? reasons : List.of();
        }
    }

    public static class SkillGapItem {
        public enum Status { MET, PARTIAL, MISSING }

        public final String skillId;
        public final String skillName;
        public final String category;
        public final int studentProficiency;
        public final int requiredProficiency;
        public final Status status;
        public final int gapLevel;
        public final boolean mandatory;
        public final String recommendation;

        public SkillGapItem(
                String skillId,
                String skillName,
                String category,
                int studentProficiency,
                int requiredProficiency,
                Status status,
                int gapLevel,
                boolean mandatory,
                String recommendation
        ) {
            this.skillId = skillId;
            this.skillName = skillName;
            this.category = category;
            this.studentProficiency = studentProficiency;
            this.requiredProficiency = requiredProficiency;
            this.status = status;
            this.gapLevel = gapLevel;
            this.mandatory = mandatory;
            this.recommendation = recommendation;
        }
    }

    public static class SkillGapAnalysis {
        public final String studentId;
        public final String studentName;
        public final String driveId;
        public final String driveTitle;
        public final String companyName;
        public final int readinessPercentage;
        public final int totalRequiredSkills;
        public final int skillsSatisfiedCount;
        public final int skillsPartialCount;
        public final int skillsMissingCount;
        public final List<SkillGapItem> skills;
        public final List<String> actionPlan;
        public final List<String> recommendedFocusSkills;

        public SkillGapAnalysis(
                String studentId,
                String studentName,
                String driveId,
                String driveTitle,
                String companyName,
                int readinessPercentage,
                int totalRequiredSkills,
                int skillsSatisfiedCount,
                int skillsPartialCount,
                int skillsMissingCount,
                List<SkillGapItem> skills,
                List<String> actionPlan,
                List<String> recommendedFocusSkills
        ) {
            this.studentId = studentId;
            this.studentName = studentName;
            this.driveId = driveId;
            this.driveTitle = driveTitle;
            this.companyName = companyName;
            this.readinessPercentage = readinessPercentage;
            this.totalRequiredSkills = totalRequiredSkills;
            this.skillsSatisfiedCount = skillsSatisfiedCount;
            this.skillsPartialCount = skillsPartialCount;
            this.skillsMissingCount = skillsMissingCount;
            this.skills = skills != null ? skills : new ArrayList<>();
            this.actionPlan = actionPlan != null ? actionPlan : new ArrayList<>();
            this.recommendedFocusSkills = recommendedFocusSkills != null ? recommendedFocusSkills : new ArrayList<>();
        }
    }

    public static class CompanyMatch {
        public final PlacementDrive drive;
        public final Company company;
        public final int matchScore;
        public final EligibilityResult eligibility;
        public final List<String> skillsMatched;
        public final List<String> skillsMissing;
        public final String explanation;

        public CompanyMatch(
                PlacementDrive drive,
                Company company,
                int matchScore,
                EligibilityResult eligibility,
                List<String> skillsMatched,
                List<String> skillsMissing,
                String explanation
        ) {
            this.drive = drive;
            this.company = company;
            this.matchScore = matchScore;
            this.eligibility = eligibility;
            this.skillsMatched = skillsMatched != null ? skillsMatched : List.of();
            this.skillsMissing = skillsMissing != null ? skillsMissing : List.of();
            this.explanation = explanation;
        }
    }

    public static class PlacementBundle {
        public final List<Company> companies;
        public final List<PlacementDrive> drives;
        public final List<Student> students;
        public final List<Skill> skills;
        public final List<Application> applications;
        public final String error;

        public PlacementBundle(
                List<Company> companies,
                List<PlacementDrive> drives,
                List<Student> students,
                List<Skill> skills,
                List<Application> applications,
                String error
        ) {
            this.companies = companies != null ? companies : List.of();
            this.drives = drives != null ? drives : List.of();
            this.students = students != null ? students : List.of();
            this.skills = skills != null ? skills : List.of();
            this.applications = applications != null ? applications : List.of();
            this.error = error;
        }
    }
}
