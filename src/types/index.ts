export type UserRole = 
  | 'PRINCIPAL' 
  | 'CHAIRMAN' 
  | 'VICE_CHAIRMAN' 
  | 'ADMIN' 
  | 'PLACEMENT_OFFICER' 
  | 'FACULTY' 
  | 'STUDENT' 
  | 'COMPANY';

export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  role: UserRole;
  department?: string;
  designation?: string;
  employeeId?: string;
  phone?: string;
  location?: string;
  address?: string;
  aboutMe?: string;
  profilePhoto?: string;
  studentRollNumber?: string;
  companyId?: string;
  companyName?: string;
  status?: 'PENDING' | 'PENDING_APPROVAL' | 'ACTIVE' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  mustChangePassword?: boolean;
  isActive?: boolean;
  lastLogin?: string;
  passwordHash?: string;
  salt?: string;
  createdAt?: string;
  approvedBy?: string;
  approvedByName?: string;
  approvedAt?: string;
  rejectionReason?: string;
  authorizedDepartments?: string[];
}

export interface AdminRequest {
  id: string;
  userId: string;
  applicantName: string;
  applicantEmail: string;
  currentRole: string;
  requestedRole: string;
  department?: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  decidedBy?: string;
  decidedByName?: string;
  decisionNotes?: string;
  createdAt: string;
  decidedAt?: string;
}

export interface ApprovalRecord {
  id: string;
  requestType: 'FACULTY_REGISTRATION' | 'STUDENT_REGISTRATION' | 'COMPANY_REGISTRATION' | 'ADMIN_PRIVILEGE';
  targetId: string;
  targetName: string;
  approverId: string;
  approverName: string;
  approverRole: string;
  action: 'APPROVED' | 'REJECTED';
  notes?: string;
  createdAt: string;
}

export interface ApprovalHistoryRecord {
  id: string;
  studentId: string;
  studentRollNumber: string;
  studentName: string;
  department: string;
  action: 'APPROVED' | 'REJECTED';
  facultyId?: string;
  facultyName?: string;
  approverId?: string;
  approverName?: string;
  timestamp: string;
  notes?: string;
}

export function normalizeDeptCode(dept: string): string {
  if (!dept) return '';
  const s = dept.trim().toUpperCase().replace(/\s+/g, '');
  if (s.includes('AIML')) return 'CSE-AIML';
  if (s.includes('DS') || s.includes('DATASCIENCE')) return 'CSE-DS';
  if (s.includes('CYBER') || s.includes('SECURITY') || s.includes('CSE-CS') || s.includes('CSECS')) return 'CSE-CS';
  if (s === 'CSE') return 'CSE';
  if (s === 'IT') return 'IT';
  if (s === 'EEE') return 'EEE';
  if (s === 'ECE') return 'ECE';
  if (s === 'MECH' || s === 'MECHANICAL') return 'MECH';
  if (s === 'CIVIL') return 'CIVIL';
  return dept.trim();
}

export const FACULTY_ASSIGNED_DEPTS: Record<string, string[]> = {
  // Username or ID mapping -> canonical department codes
  'faculty_lavanya': ['CSE', 'CSE-AIML', 'CSE-DS'],
  'usr-fac-1': ['CSE', 'CSE-AIML', 'CSE-DS'],
  'faculty_aparna': ['CSE-CS', 'IT'],
  'usr-fac-2': ['CSE-CS', 'IT'],
  'faculty_rajashakar': ['EEE', 'ECE', 'MECH', 'CIVIL'],
  'usr-fac-3': ['EEE', 'ECE', 'MECH', 'CIVIL'],
};

export function getFacultyAuthorizedDepts(facultyUsernameOrId: string): string[] {
  if (!facultyUsernameOrId) return [];
  const key = facultyUsernameOrId.toLowerCase().trim();
  if (FACULTY_ASSIGNED_DEPTS[key]) return FACULTY_ASSIGNED_DEPTS[key];
  if (key.includes('lavanya')) return FACULTY_ASSIGNED_DEPTS['faculty_lavanya'];
  if (key.includes('aparna')) return FACULTY_ASSIGNED_DEPTS['faculty_aparna'];
  if (key.includes('rajashakar') || key.includes('raja')) return FACULTY_ASSIGNED_DEPTS['faculty_rajashakar'];
  return [];
}

export function isFacultyAuthorizedForStudent(facultyUsernameOrId: string, studentBranch: string): boolean {
  const authorized = getFacultyAuthorizedDepts(facultyUsernameOrId);
  if (authorized.length === 0) return false;
  const normalizedStudentDept = normalizeDeptCode(studentBranch);
  return authorized.some((d) => normalizeDeptCode(d) === normalizedStudentDept);
}

export type Branch = 
  | 'CSE' 
  | 'CSE - AIML' 
  | 'CSE - DS' 
  | 'CSE - Cyber Security' 
  | 'IT' 
  | 'ECE' 
  | 'EEE' 
  | 'MECH' 
  | 'CIVIL';

export type SkillProficiency = 1 | 2 | 3 | 4 | 5;

export const PROFICIENCY_LABELS: Record<SkillProficiency, string> = {
  1: 'Beginner',
  2: 'Basic',
  3: 'Intermediate',
  4: 'Advanced',
  5: 'Expert',
};

export type SkillCategory = 
  | 'Programming Languages'
  | 'Data Structures & Algorithms'
  | 'Database Management'
  | 'Web Development'
  | 'AI & Machine Learning'
  | 'Cloud Computing'
  | 'Operating Systems & Networks'
  | 'Communication'
  | 'Aptitude'
  | 'Software Tools';

export interface Skill {
  id: string;
  name: string;
  category: SkillCategory;
  description: string;
}

export interface StudentSkill {
  skillId: string;
  proficiency: SkillProficiency;
  verifiedByFaculty: boolean;
  verifiedByFacultyId?: string;
  verifiedAt?: string;
}

export interface StudentProject {
  id: string;
  title: string;
  description: string;
  technologies: string[];
  link?: string;
  duration?: string;
}

export interface Certification {
  id: string;
  name: string;
  issuingOrg: string;
  issueDate: string;
  credentialId?: string;
  credentialUrl?: string;
}

export interface Internship {
  id: string;
  companyName: string;
  role: string;
  duration: string;
  location?: string;
  description: string;
}

export interface AchievementItem {
  id: string;
  title: string;
  category: 'AWARD' | 'WORKSHOP' | 'HACKATHON';
  organizer?: string;
  eventDate?: string;
  description?: string;
  credentialUrl?: string;
}

export interface SocialLinks {
  linkedin?: string;
  github?: string;
  portfolio?: string;
  leetcode?: string;
  hackerrank?: string;
  codechef?: string;
  geeksforgeeks?: string;
  kaggle?: string;
  googleDeveloper?: string;
}

export interface CustomLink {
  id: string;
  title: string;
  url: string;
}

export interface PublicationItem {
  id: string;
  title: string;
  journalOrConference: string;
  year: string;
  authors?: string;
  url?: string;
}

export interface FacultyProfileData {
  id?: string;
  facultyId: string;
  employeeId?: string;
  department: string;
  designation: string;
  qualifications?: string;
  areasOfExpertise: string[];
  subjectsHandled: string[];
  researchInterests?: string;
  facultyApproved?: boolean;
  adminApproved?: boolean;
  status?: string;
  publications: PublicationItem[];
  certifications: Certification[];
}

export interface Student {
  id: string;
  rollNumber: string; // e.g. 22R11A0501
  fullName: string;
  email: string;
  phone: string;
  branch: Branch;
  section?: string;
  currentSemester?: number;
  cgpa: number; // e.g. 8.42
  tenthPercentage: number;
  interOrDiplomaPercentage: number;
  graduationYear: number; // e.g. 2026
  backlogs: number;
  skills: StudentSkill[];
  projects: StudentProject[];
  certifications: Certification[];
  internships: Internship[];
  achievements?: AchievementItem[];
  dob?: string;
  gender?: string;
  address?: string;
  location?: string;
  aboutMe?: string;
  profilePhoto?: string;
  technicalSkills?: string[];
  programmingLanguages?: string[];
  frameworksTechnologies?: string[];
  softSkills?: string[];
  careerInterests?: string[];
  preferredJobRoles?: string[];
  socialLinks?: SocialLinks;
  customLinks?: CustomLink[];
  resumeUrl?: string;
  placementStatus: 'UNPLACED' | 'SHORTLISTED' | 'PLACED' | 'OPTED_OUT';
  placedCompanyId?: string;
  placedPackageLpa?: number;
  approvalStatus?: 'PENDING' | 'PENDING_APPROVAL' | 'ACTIVE' | 'APPROVED' | 'REJECTED';
  approvedByFacultyId?: string;
  approvedByName?: string;
  approvedAt?: string;
  academicYear?: number;
  registeredAt?: string;
  rejectionReason?: string;
}

export interface Company {
  id: string;
  name: string;
  industry: string;
  website: string;
  location: string;
  description: string;
  hrName: string;
  hrEmail: string;
  hrPhone: string;
  recruiterName?: string;
  recruiterDesignation?: string;
  companyLogo?: string;
  linkedinUrl?: string;
  tier: 'Tier 1 (Dream)' | 'Tier 2 (Super Dream)' | 'Core' | 'Standard IT';
  totalDrivesConducted: number;
  createdAt: string;
  status?: 'PENDING' | 'ACTIVE' | 'REJECTED';
  approvalStatus?: string;
  approvedByAdminId?: string;
  approvedByName?: string;
  approvedAt?: string;
  rejectionReason?: string;
}

export interface UnifiedUserProfile {
  user: User;
  socialLinks: SocialLinks;
  customLinks: CustomLink[];
  student?: Student | null;
  faculty?: FacultyProfileData | null;
  company?: Partial<Company> | null;
  completionPercentage: number;
  missingSections: string[];
  isOwnProfile?: boolean;
}

export type DriveStatus = 
  | 'PLANNED' 
  | 'UPCOMING' 
  | 'OPEN' 
  | 'CLOSED' 
  | 'COMPLETED' 
  | 'CANCELLED';

export interface DriveRequiredSkill {
  skillId: string;
  minProficiency: SkillProficiency;
  isMandatory: boolean;
}

export interface PlacementDrive {
  id: string;
  companyId: string;
  companyName: string;
  jobRole: string;
  jobDescription: string;
  salaryPackageLpa: number; // in Lakhs Per Annum e.g. 9.5
  eligibleBranches: Branch[];
  minCgpa: number;
  maxBacklogsAllowed: number;
  targetGraduationYear: number;
  requiredSkills: DriveRequiredSkill[];
  driveDate: string;
  applicationDeadline: string;
  availablePositions: number;
  status: DriveStatus;
  selectionProcess: string; // e.g. "Online Test -> Technical Interview -> HR"
  createdAt: string;
}

export type ApplicationStage = 
  | 'APPLICATION_SUBMITTED'
  | 'APPLICATION_REVIEWED'
  | 'SHORTLISTED'
  | 'ONLINE_ASSESSMENT'
  | 'TECHNICAL_INTERVIEW'
  | 'HR_INTERVIEW'
  | 'SELECTED'
  | 'OFFERED'
  | 'JOINED'
  | 'REJECTED'
  | 'WITHDRAWN';

export const APPLICATION_STAGE_LABELS: Record<ApplicationStage, string> = {
  APPLICATION_SUBMITTED: 'Application Submitted',
  APPLICATION_REVIEWED: 'Application Reviewed',
  SHORTLISTED: 'Shortlisted',
  ONLINE_ASSESSMENT: 'Online Assessment',
  TECHNICAL_INTERVIEW: 'Technical Interview',
  HR_INTERVIEW: 'HR Interview',
  SELECTED: 'Selected',
  OFFERED: 'Offered',
  JOINED: 'Joined',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
};

export const STAGE_ORDER: ApplicationStage[] = [
  'APPLICATION_SUBMITTED',
  'APPLICATION_REVIEWED',
  'SHORTLISTED',
  'ONLINE_ASSESSMENT',
  'TECHNICAL_INTERVIEW',
  'HR_INTERVIEW',
  'SELECTED',
  'OFFERED',
  'JOINED',
];

export interface StageHistoryEntry {
  stage: ApplicationStage;
  timestamp: string;
  updatedBy: string;
  notes?: string;
}

export interface Application {
  id: string;
  driveId: string;
  studentId: string;
  studentRollNumber: string;
  studentName: string;
  studentBranch: Branch;
  studentCgpa: number;
  companyId: string;
  companyName: string;
  jobRole: string;
  currentStage: ApplicationStage;
  stageHistory: StageHistoryEntry[];
  appliedAt: string;
  finalOfferLpa?: number;
  rejectionReason?: string;
}

export interface EligibilityResult {
  isEligible: boolean;
  score: number; // 0 - 100
  criteriaChecks: {
    cgpa: { satisfied: boolean; studentVal: number; requiredVal: number; message: string };
    branch: { satisfied: boolean; studentVal: Branch; allowedBranches: Branch[]; message: string };
    graduationYear: { satisfied: boolean; studentVal: number; requiredVal: number; message: string };
    backlogs: { satisfied: boolean; studentVal: number; maxAllowed: number; message: string };
    skills: {
      satisfied: boolean;
      totalRequired: number;
      satisfiedCount: number;
      details: {
        skillId: string;
        skillName: string;
        studentProficiency: SkillProficiency | 0;
        requiredProficiency: SkillProficiency;
        satisfied: boolean;
        isMandatory: boolean;
        gap: number;
      }[];
      message: string;
    };
  };
  reasons: string[];
}

export interface SkillGapItem {
  skillId: string;
  skillName: string;
  category: SkillCategory;
  studentProficiency: SkillProficiency | 0;
  requiredProficiency: SkillProficiency;
  status: 'MET' | 'PARTIAL' | 'MISSING';
  gapLevel: number;
  recommendation: string;
}

export interface SkillGapAnalysis {
  studentId: string;
  studentName: string;
  driveId: string;
  driveTitle: string;
  companyName: string;
  readinessPercentage: number;
  totalRequiredSkills: number;
  skillsSatisfiedCount: number;
  skillsPartialCount: number;
  skillsMissingCount: number;
  skills: SkillGapItem[];
  actionPlan: string[];
}

export interface CompanyMatch {
  drive: PlacementDrive;
  company: Company;
  matchScore: number; // 0 - 100
  eligibility: EligibilityResult;
  skillsMatched: string[];
  skillsMissing: string[];
  explanation: string;
}

export interface ActivityLog {
  id: string;
  timestamp: string;
  userId: string;
  userName: string;
  action: string;
  category: 'STUDENT' | 'COMPANY' | 'DRIVE' | 'APPLICATION' | 'SKILL' | 'AUTH' | 'SYSTEM';
  details: string;
}
