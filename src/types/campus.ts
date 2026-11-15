export type AnnouncementCategory = 
  | 'EXAMINATION' 
  | 'ACADEMIC' 
  | 'PLACEMENTS' 
  | 'CIRCULAR' 
  | 'FESTIVAL';

export interface Announcement {
  id: string;
  title: string;
  date: string;
  category: AnnouncementCategory;
  summary: string;
  link: string;
  isImportant?: boolean;
  attachmentName?: string;
  postedBy: string;
}

export interface ExamResultItem {
  id: string;
  title: string;
  examMonthYear: string;
  releaseDate: string;
  regulations: string[];
  branches: string[];
  portalUrl: string;
  type: 'REGULAR' | 'SUPPLEMENTARY' | 'REVALUATION' | 'OTC';
}

export type ClubCategory = 
  | 'TECHNICAL' 
  | 'CULTURAL' 
  | 'CAREER' 
  | 'ENTREPRENEURSHIP' 
  | 'MEDIA' 
  | 'SOCIAL_SERVICE' 
  | 'ALUMNI';

export interface CollegeClub {
  id: string;
  name: string;
  shortName: string;
  category: ClubCategory;
  description: string;
  activities: string[];
  facultyCoordinator: string;
  studentLead: string;
  portalUrl: string;
  badge: string;
  color: string;
}

export interface CollegeFestival {
  id: string;
  name: string;
  tagline: string;
  type: 'CULTURAL' | 'TECHNICAL_SYMPOSIUM' | 'DEPARTMENTAL_TECH_FEST';
  description: string;
  highlights: string[];
  scheduleMonth: string;
  departments: string[];
  portalUrl: string;
}

export interface StudentResource {
  id: string;
  title: string;
  shortDescription: string;
  category: 'EXAM_DESK' | 'LIBRARY' | 'CAREER_TOOLS' | 'ACADEMIC_CURRICULUM' | 'FEE_PORTAL';
  url: string;
  actionText: string;
  isDirectPortalLink?: boolean;
}

export interface BranchIntake {
  branchCode: string;
  branchName: string;
  degree: string;
  sanctionedIntake: number;
  establishedYear: number;
}
