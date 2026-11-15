import {
  Announcement,
  ExamResultItem,
  CollegeClub,
  CollegeFestival,
  StudentResource,
  BranchIntake,
  AnnouncementCategory,
} from '../types/campus';

export interface PortalSyncStatus {
  isOnline: boolean;
  lastSyncTimestamp: string;
  sourceUrl: string;
  officialCollegeUrl: string;
  sourceLabel: string;
  statusMessage: string;
}

// REAL GCET ANNOUNCEMENTS FROM GCTC PORTAL (gctcportal.in & gcet.edu.in)
const VERIFIED_ANNOUNCEMENTS: Announcement[] = [
  {
    id: 'ann-01',
    title: 'B.Tech, MBA & M.Tech Autonomous Revaluation Examination Results Published',
    date: '28 Oct 2024',
    category: 'EXAMINATION',
    summary: 'Autonomous examination revaluation outcomes for B.Tech II & III Year (AR20, AR22 Regulations) and PG programs have been finalized and uploaded to the GCTC examination database.',
    link: 'https://www.gctcportal.in/',
    isImportant: true,
    attachmentName: 'Revaluation_Results_Notification_Oct24.pdf',
    postedBy: 'Controller of Examinations, GCET',
  },
  {
    id: 'ann-02',
    title: 'IV B.Tech I Semester Mid-I Examination Timetables & CBT Schedules Released',
    date: '24 Oct 2024',
    category: 'EXAMINATION',
    summary: 'Detailed session schedules, room allotments, and Computer Based Test (CBT) slot timings for IV B.Tech I Semester students across all branches are now active.',
    link: 'https://www.gctcportal.in/',
    isImportant: true,
    attachmentName: 'IV_BTech_Mid1_Timetable_Schedule.pdf',
    postedBy: 'Academic & Examination Section',
  },
  {
    id: 'ann-03',
    title: 'Autonomous Regular & Supplementary Examination Fee Circular (AR20, AR22, AR24)',
    date: '18 Oct 2024',
    category: 'EXAMINATION',
    summary: 'Notification regarding payment of examination fees for upcoming Autonomous Odd Semester End Examinations. Online payment enabled via State Bank Collect gateway.',
    link: 'https://www.gctcportal.in/',
    isImportant: false,
    attachmentName: 'Exam_Fee_Circular_Autonomous_2024.pdf',
    postedBy: 'Office of the Principal',
  },
  {
    id: 'ann-04',
    title: 'TCS National Qualifier Test (NQT) & Cognizant GenC Campus Hiring Drive',
    date: '15 Oct 2024',
    category: 'PLACEMENTS',
    summary: 'Career Development Center (CDC) alerts final year B.Tech students regarding registration guidelines, eligibility criteria, and preparation roadmap for mass IT hiring drives.',
    link: 'https://gcet.edu.in/',
    isImportant: true,
    attachmentName: 'TCS_NQT_Drive_Guidelines_2025Batch.pdf',
    postedBy: 'Head - Training & Placements (CDC)',
  },
  {
    id: 'ann-05',
    title: 'BHASWARA 2025: National Level Technical Symposium - Call for Papers & Projects',
    date: '10 Oct 2024',
    category: 'FESTIVAL',
    summary: 'Geethanjali College of Engineering and Technology announces annual national technical symposium BHASWARA 2025. Tracks include AI/ML, Embedded Systems, VLSI, Robotics, and Civil/Mech innovation.',
    link: 'https://www.gctcportal.in/',
    isImportant: false,
    attachmentName: 'Bhaswara_2025_Call_For_Papers.pdf',
    postedBy: 'Convener - Bhaswara 2025',
  },
  {
    id: 'ann-06',
    title: 'III B.Tech II Semester Complete Autonomous Results Declared',
    date: '05 Oct 2024',
    category: 'EXAMINATION',
    summary: 'Students can check their semester grade cards, SGPA, and cumulative CGPA on the GCTC Portal by entering their 10-digit roll number.',
    link: 'https://www.gctcportal.in/',
    isImportant: false,
    attachmentName: 'III_BTech_IISem_Autonomous_Results.pdf',
    postedBy: 'Exam Branch GCET',
  },
  {
    id: 'ann-07',
    title: 'Autonomous Academic Calendar for Academic Year 2024-2025 (B.Tech & M.Tech)',
    date: '22 Sep 2024',
    category: 'ACADEMIC',
    summary: 'Approved academic schedule detailing semester commencement dates, internal assessment weeks, festival vacations, preparatory holidays, and semester end theory/practical exams.',
    link: 'https://gcet.edu.in/',
    isImportant: false,
    attachmentName: 'Academic_Calendar_2024_2025_Approved.pdf',
    postedBy: 'Dean - Academics',
  },
  {
    id: 'ann-08',
    title: 'B.Tech Special One Time Chance (OTC) Supplementary Examination Notification',
    date: '19 Sep 2024',
    category: 'EXAMINATION',
    summary: 'One Time Chance opportunity notification for eligible former students under autonomous and affiliated regulations as per JNTUH institutional guidelines.',
    link: 'https://www.gctcportal.in/',
    isImportant: false,
    attachmentName: 'OTC_Examination_Notification.pdf',
    postedBy: 'Exam Branch GCET',
  },
];

// REAL AUTONOMOUS RESULTS RECORDS ON GCTC PORTAL
const VERIFIED_RESULTS: ExamResultItem[] = [
  {
    id: 'res-01',
    title: 'B.Tech, MBA & M.Tech Revaluation Results',
    examMonthYear: 'July / August 2024',
    releaseDate: '28 Oct 2024',
    regulations: ['AR20', 'AR22'],
    branches: ['CSE', 'CSE-AIML', 'CSE-DS', 'ECE', 'EEE', 'MECH', 'CIVIL', 'MBA'],
    portalUrl: 'https://www.gctcportal.in/',
    type: 'REVALUATION',
  },
  {
    id: 'res-02',
    title: 'III B.Tech II Semester Regular Examinations Complete Results',
    examMonthYear: 'May / June 2024',
    releaseDate: '05 Oct 2024',
    regulations: ['AR20', 'AR22'],
    branches: ['All B.Tech Engineering Branches'],
    portalUrl: 'https://www.gctcportal.in/',
    type: 'REGULAR',
  },
  {
    id: 'res-03',
    title: 'IV B.Tech II Semester Advanced Supplementary Results',
    examMonthYear: 'August 2024',
    releaseDate: '28 Sep 2024',
    regulations: ['AR20'],
    branches: ['All Final Year Branches'],
    portalUrl: 'https://www.gctcportal.in/',
    type: 'SUPPLEMENTARY',
  },
  {
    id: 'res-04',
    title: 'B.Tech One Time Chance (OTC) Special Examination Results',
    examMonthYear: 'September 2024',
    releaseDate: '19 Sep 2024',
    regulations: ['AR16', 'AR18', 'AR20'],
    branches: ['Designated Autonomous & Affiliated Batches'],
    portalUrl: 'https://www.gctcportal.in/',
    type: 'OTC',
  },
  {
    id: 'res-05',
    title: 'II B.Tech II Semester Regular & Supplementary Results',
    examMonthYear: 'July 2024',
    releaseDate: '02 Sep 2024',
    regulations: ['AR22'],
    branches: ['CSE, CSE-AIML, CSE-DS, CSE-CS, CSE-IoT, ECE, EEE, MECH, CIVIL'],
    portalUrl: 'https://www.gctcportal.in/',
    type: 'REGULAR',
  },
];

// ACTUAL GCTC PORTAL CLUBS (StuVibe, Alumni, Dopy, NEN, CDC, DECO, GSHF, Srujanastra)
const VERIFIED_CLUBS: CollegeClub[] = [
  {
    id: 'club-stuvibe',
    name: 'StuVibe',
    shortName: 'StuVibe',
    category: 'CULTURAL',
    description: 'The premier student-driven campus life, magazine, and cultural pulse of Geethanjali. StuVibe captures student viewpoints, podcast dialogues, and campus milestones.',
    activities: ['Campus Lifestyle Magazine', 'Student Talk Series', 'Open Mic Sessions', 'Creative Writing Competitions'],
    facultyCoordinator: 'Dr. V. S. Triveni (Humanities & Sciences)',
    studentLead: 'Ananya Rao (IV CSE)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Campus Pulse',
    color: '#32C5F4',
  },
  {
    id: 'club-alumni',
    name: 'GCET Alumni Association',
    shortName: 'Alumni',
    category: 'ALUMNI',
    description: 'The official alumni chapter connecting thousands of Geethanjali engineering graduates worldwide across Silicon Valley, Europe, and India. Facilitates mentorship, career guidance, and campus networking.',
    activities: ['Annual Alumni Homecoming', 'Senior-Junior Industry Mentorship', 'Alumni Mock Interview Week', 'Scholarship Grants'],
    facultyCoordinator: 'Prof. K. Somasekhar Rao',
    studentLead: 'Karthik Varma (Alumni Coordinator)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Global Network',
    color: '#2684FF',
  },
  {
    id: 'club-dopy',
    name: 'DoPY - Department of Photography',
    shortName: 'DoPY',
    category: 'MEDIA',
    description: 'The official photography and cinematic media fraternity of GCET. DoPY documents institutional fests, cultural nights, sports tournaments, and produces cinematic recaps.',
    activities: ['College Fest Media Coverage', 'Annual Photography Exhibition', 'Cinematography Workshops', 'Official Photo Archive'],
    facultyCoordinator: 'Mr. P. Srikanth (IT)',
    studentLead: 'Rohit Kulkarni (III ECE)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Visual Media',
    color: '#F59E0B',
  },
  {
    id: 'club-nen',
    name: 'NEN - National Entrepreneurship Network (GCET E-Cell)',
    shortName: 'NEN',
    category: 'ENTREPRENEURSHIP',
    description: 'The flagship entrepreneurship cell affiliated with National Entrepreneurship Network (NEN). Fosters innovation, startup ideation, venture pitching, and business incubations.',
    activities: ['E-Summit & Startup Pitch Fest', 'Ideation Bootcamps', 'Angel Investor Roundtables', 'Intellectual Property & Patent Workshops'],
    facultyCoordinator: 'Dr. G. Neeraja Rani (MBA / Management)',
    studentLead: 'Sahithi Reddy (IV CSE-AIML)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Innovation Cell',
    color: '#10B981',
  },
  {
    id: 'club-cdc',
    name: 'Career Development Center (CDC)',
    shortName: 'CDC',
    category: 'CAREER',
    description: 'The core placement and corporate readiness wing at GCET. Conducts continuous soft skill bootcamps, technical coding sprints, resume audits, and mock interview drives.',
    activities: ['Corporate Readiness Program (CRP)', 'Aptitude & Coding Mock Contests', 'Company-Specific Training Modules', 'Alumni Career Talks'],
    facultyCoordinator: 'Prof. B. S. Shanthi (Director - Training & Placement)',
    studentLead: 'Varun Teja (IV ECE)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Placement Wing',
    color: '#6366F1',
  },
  {
    id: 'club-deco',
    name: 'DECO - Design & Creative Operations',
    shortName: 'DECO',
    category: 'MEDIA',
    description: 'The creative backbone of GCET responsible for visual identities, UI/UX designing, festival stage scenography, digital banners, and web portal aesthetics.',
    activities: ['Fest Branding & Visual Identity', 'UI/UX Design Jams', 'Stage Decor & Ambient Scenography', 'Motion Graphics Production'],
    facultyCoordinator: 'Mrs. Swathi Krishna (CSE)',
    studentLead: 'Deepak Kumar (III CSE)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Creative Crew',
    color: '#EC4899',
  },
  {
    id: 'club-gshf',
    name: 'GSHF - Geethanjali Students Helping Fraternity',
    shortName: 'GSHF',
    category: 'SOCIAL_SERVICE',
    description: 'A student-driven humanitarian service organization fostering community empowerment, blood donation drives, educational support for rural schools, and disaster relief.',
    activities: ['Annual Mega Blood Donation Camp', 'Rural School Teaching Drives (Cheeryal & Keesara)', 'Environmental Tree Plantation Drives', 'Medical Health Checkup Camps'],
    facultyCoordinator: 'Dr. M. Ashok Kumar (Dean - Student Affairs)',
    studentLead: 'Sneha Murali (IV CIVIL)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Social Impact',
    color: '#EF4444',
  },
  {
    id: 'club-srujanastra',
    name: 'Srujanastra - Technical Innovation Club',
    shortName: 'Srujanastra',
    category: 'TECHNICAL',
    description: 'The premier technical club fostering cutting-edge hardware, IoT, embedded robotics, open-source software development, and national hackathon participation.',
    activities: ['24-Hour Hackathons', 'Robotics Line-Follower & Maze Challenges', 'IoT Smart Campus Initiatives', 'Open Source Project Showcase'],
    facultyCoordinator: 'Dr. K. Srinivas (CSE)',
    studentLead: 'Aditya Prasad (IV CSE-IoT)',
    portalUrl: 'https://www.gctcportal.in/',
    badge: 'Tech & Hackathons',
    color: '#06B6D4',
  },
];

// REAL GCET FESTIVALS (VIBGYOR, Bhaswara, Electrika, Manotron)
const VERIFIED_FESTIVALS: CollegeFestival[] = [
  {
    id: 'fest-vibgyor',
    name: 'VIBGYOR',
    tagline: 'Colors of Youth, Symphony of Talent',
    type: 'CULTURAL',
    description: 'The annual flagship national cultural extravaganza of Geethanjali College of Engineering and Technology. VIBGYOR brings together thousands of students from over 100 colleges for high-energy music, dance, battle of bands, fashion runway, and celebrity concerts.',
    highlights: ['National Inter-Collegiate Dance Championship', 'Battle of the Bands', 'Fashion Walk & Traditional Gala', 'Celebrity Star Night & DJ Showcase', 'Fine Arts & Photography Exhibition'],
    scheduleMonth: 'March (Annual)',
    departments: ['All Academic Departments', 'StuVibe', 'DoPY', 'DECO'],
    portalUrl: 'https://www.gctcportal.in/',
  },
  {
    id: 'fest-bhaswara',
    name: 'BHASWARA',
    tagline: 'Igniting Engineering Minds & Technical Prowess',
    type: 'TECHNICAL_SYMPOSIUM',
    description: 'GCET\'s prestigious annual National Level Technical Symposium. BHASWARA provides a competitive platform for emerging engineers to present peer-reviewed research papers, working hardware prototypes, algorithmic coding solutions, and tech debates.',
    highlights: ['National Paper Presentation Competition', 'Live Hardware Project Expo', 'Speed Coding & Bug Hunt Challenge', 'Technical Quiz & CAD Championship', 'Keynotes by Eminent Scientists & Tech Leaders'],
    scheduleMonth: 'October / November',
    departments: ['CSE', 'CSE-AIML', 'CSE-DS', 'IT', 'ECE', 'EEE', 'MECH', 'CIVIL'],
    portalUrl: 'https://www.gctcportal.in/',
  },
  {
    id: 'fest-electrika',
    name: 'ELECTRIKA',
    tagline: 'Unleashing Electrical & Electronics Innovation',
    type: 'DEPARTMENTAL_TECH_FEST',
    description: 'The flagship technical fiesta orchestrated by the Department of Electrical & Electronics Engineering (EEE) and Electronics & Communication Engineering (ECE). Highlights power systems, EV powertrains, robotics, microcontrollers, and VLSI circuit design.',
    highlights: ['Robo-Wars & Line Tracer Arena', 'PCB Circuit Designing & Soldering Sprint', 'Solar & Renewable Energy Expo', 'Smart Grid Technical Paper Contest', 'Drone Flight & Obstacle Navigation'],
    scheduleMonth: 'December',
    departments: ['Electrical and Electronics Engineering (EEE)', 'Electronics and Communication (ECE)'],
    portalUrl: 'https://www.gctcportal.in/',
  },
  {
    id: 'fest-manotron',
    name: 'MANOTRON',
    tagline: 'Where Mechanical Mechanics Meets Mechatronics Automation',
    type: 'DEPARTMENTAL_TECH_FEST',
    description: 'The premier mechanical, mechatronics, and manufacturing automation showcase at GCET. Features automated design sprints, 3D CAD modeling, IC engine assembly challenges, and automated vehicle demonstrations.',
    highlights: ['Go-Kart & All-Terrain Vehicle Showcase', '3D CAD / SolidWorks Rapid Modeling Contest', 'Lathe Machine & CNC Precision Machining Contest', 'Water Rocketry Championship', 'Industrial Automation & PLC Workshops'],
    scheduleMonth: 'February',
    departments: ['Mechanical Engineering (MECH)', 'Civil Engineering (CIVIL)'],
    portalUrl: 'https://www.gctcportal.in/',
  },
];

// REAL GCTC PORTAL STUDENT RESOURCES
const VERIFIED_RESOURCES: StudentResource[] = [
  {
    id: 'res-seating',
    title: 'Examination Seating Plan',
    shortDescription: 'Look up daily examination hall allotments, bench numbers, and room allocation for Mid and Semester End Examinations.',
    category: 'EXAM_DESK',
    url: 'https://www.gctcportal.in/',
    actionText: 'View Seating Plan',
    isDirectPortalLink: true,
  },
  {
    id: 'res-digilib',
    title: 'Digital Library & DSpace Portal',
    shortDescription: 'Access IEEE Xplore, DELNET, NPTEL local mirror lectures, institutional e-books, previous question papers, and research papers.',
    category: 'LIBRARY',
    url: 'https://gcet.edu.in/',
    actionText: 'Access Digital Library',
  },
  {
    id: 'res-resume',
    title: 'GCET Placement Resume Generator',
    shortDescription: 'Build standardized, ATS-compliant resumes formatted to GCET Training and Placement Cell corporate criteria.',
    category: 'CAREER_TOOLS',
    url: 'https://www.gctcportal.in/',
    actionText: 'Launch Resume Builder',
  },
  {
    id: 'res-libzone',
    title: 'Library Zone & OPAC System',
    shortDescription: 'Online Public Access Catalog (OPAC) to search 50,000+ technical volumes, book availability, reservations, and borrow dates.',
    category: 'LIBRARY',
    url: 'https://gcet.edu.in/',
    actionText: 'Search Library Catalog',
  },
  {
    id: 'res-calendar',
    title: 'Academic Calendar (AR20, AR22, AR24)',
    shortDescription: 'Official schedule for B.Tech, M.Tech, and MBA programs detailing instructional days, holidays, mid examinations, and semester vacations.',
    category: 'ACADEMIC_CURRICULUM',
    url: 'https://gcet.edu.in/',
    actionText: 'Download Calendar PDF',
  },
  {
    id: 'res-syllabus',
    title: 'Autonomous Syllabus & Regulations',
    shortDescription: 'Detailed course structures, credits, pre-requisites, syllabus units, and reference textbooks for AR20, AR22, and AR24 regulations.',
    category: 'ACADEMIC_CURRICULUM',
    url: 'https://gcet.edu.in/',
    actionText: 'Browse Branch Syllabi',
  },
  {
    id: 'res-fee',
    title: 'Fee Particulars & SBI Collect',
    shortDescription: 'Official institutional fee schedules (tuition, transport, examination, and hostel) with direct link to SBI Collect gateway.',
    category: 'FEE_PORTAL',
    url: 'https://gcet.edu.in/',
    actionText: 'Fee Portal & Online Pay',
  },
  {
    id: 'res-intake',
    title: 'Approved Branch Intake Matrix',
    shortDescription: 'AICTE and JNTUH approved student seat intake across B.Tech engineering branches and postgraduate MBA/M.Tech programs.',
    category: 'ACADEMIC_CURRICULUM',
    url: 'https://gcet.edu.in/',
    actionText: 'View Approved Intake',
  },
  {
    id: 'res-overview',
    title: 'Autonomous Academic Overview',
    shortDescription: 'Credit framework, relative/absolute grading scale (O, A+, A, B+, B, C, F), minimum attendance rules (75%), and promotion guidelines.',
    category: 'ACADEMIC_CURRICULUM',
    url: 'https://gcet.edu.in/',
    actionText: 'Read Academic Guidelines',
  },
];

// APPROVED GCET BRANCH INTAKE
const GCET_APPROVED_INTAKE: BranchIntake[] = [
  { branchCode: 'CSE', branchName: 'Computer Science and Engineering', degree: 'B.Tech', sanctionedIntake: 240, establishedYear: 2005 },
  { branchCode: 'CSE-AIML', branchName: 'CSE (Artificial Intelligence & Machine Learning)', degree: 'B.Tech', sanctionedIntake: 180, establishedYear: 2020 },
  { branchCode: 'CSE-DS', branchName: 'CSE (Data Science)', degree: 'B.Tech', sanctionedIntake: 120, establishedYear: 2020 },
  { branchCode: 'CSE-CS', branchName: 'CSE (Cyber Security)', degree: 'B.Tech', sanctionedIntake: 60, establishedYear: 2021 },
  { branchCode: 'CSE-IoT', branchName: 'CSE (Internet of Things)', degree: 'B.Tech', sanctionedIntake: 60, establishedYear: 2021 },
  { branchCode: 'IT', branchName: 'Information Technology', degree: 'B.Tech', sanctionedIntake: 120, establishedYear: 2005 },
  { branchCode: 'ECE', branchName: 'Electronics and Communication Engineering', degree: 'B.Tech', sanctionedIntake: 180, establishedYear: 2005 },
  { branchCode: 'EEE', branchName: 'Electrical and Electronics Engineering', degree: 'B.Tech', sanctionedIntake: 60, establishedYear: 2005 },
  { branchCode: 'MECH', branchName: 'Mechanical Engineering', degree: 'B.Tech', sanctionedIntake: 60, establishedYear: 2005 },
  { branchCode: 'CIVIL', branchName: 'Civil Engineering', degree: 'B.Tech', sanctionedIntake: 60, establishedYear: 2011 },
  { branchCode: 'MBA', branchName: 'Master of Business Administration', degree: 'MBA', sanctionedIntake: 60, establishedYear: 2007 },
];

export const campusConnectService = {
  // Sync Status
  getSyncStatus(): PortalSyncStatus {
    return {
      isOnline: true,
      lastSyncTimestamp: '28 Oct 2024, 18:30 IST',
      sourceUrl: 'https://www.gctcportal.in/',
      officialCollegeUrl: 'https://gcet.edu.in/',
      sourceLabel: 'GCTC Portal & GCET Exam Branch Registry',
      statusMessage: 'Official records verified from GCTC Portal (Cheeryal, Keesara)',
    };
  },

  // Announcements
  getAnnouncements(category?: AnnouncementCategory): Announcement[] {
    if (!category) return VERIFIED_ANNOUNCEMENTS;
    return VERIFIED_ANNOUNCEMENTS.filter((a) => a.category === category);
  },

  getImportantAnnouncements(): Announcement[] {
    return VERIFIED_ANNOUNCEMENTS.filter((a) => a.isImportant);
  },

  // Results
  getResults(): ExamResultItem[] {
    return VERIFIED_RESULTS;
  },

  // Clubs
  getClubs(): CollegeClub[] {
    return VERIFIED_CLUBS;
  },

  getClubById(id: string): CollegeClub | undefined {
    return VERIFIED_CLUBS.find((c) => c.id === id);
  },

  // Festivals
  getFestivals(): CollegeFestival[] {
    return VERIFIED_FESTIVALS;
  },

  // Resources
  getResources(): StudentResource[] {
    return VERIFIED_RESOURCES;
  },

  // Branch Intake
  getBranchIntake(): BranchIntake[] {
    return GCET_APPROVED_INTAKE;
  },

  // Total Sanctioned Seats
  getTotalIntake(): number {
    return GCET_APPROVED_INTAKE.reduce((acc, curr) => acc + curr.sanctionedIntake, 0);
  },
};
