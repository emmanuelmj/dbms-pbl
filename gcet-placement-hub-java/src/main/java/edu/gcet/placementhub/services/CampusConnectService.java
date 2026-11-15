package edu.gcet.placementhub.services;

import edu.gcet.placementhub.models.CampusConnectModels.*;
import edu.gcet.placementhub.models.GctcAnnouncement;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Exact Java port of src/services/campusConnectService.ts and GCET_COLLEGE_DETAILS.
 */
public class CampusConnectService {

    public static final String COLLEGE_NAME = "Geethanjali College of Engineering and Technology";
    public static final String COLLEGE_SHORT_NAME = "GCET";
    public static final String COLLEGE_AUTONOMOUS_STATUS = "UGC Autonomous Institution · Affiliated to JNTU Hyderabad";
    public static final String COLLEGE_ACCREDITATION = "Accredited by NAAC with A+ Grade · NBA Accredited Branches";
    public static final String COLLEGE_ADDRESS = "Sy. No. 33 & 34, Cheeryal (V), Keesara (M), Medchal-Malkajgiri District, Telangana, India - PIN: 501301";
    public static final String CONTACT_FRONT_OFFICE = "+91 9182058187";
    public static final String CONTACT_EXAM_BRANCH = "+91 9182058190";
    public static final String CONTACT_ACADEMIC = "+91 9182058194";
    public static final String CONTACT_ACCOUNTS = "+91 9182058196 / +91 9182058192";
    public static final String CONTACT_ADMISSIONS_AO = "+91 9866308271";
    public static final String EMAIL_GENERAL = "info@gcet.edu.in";
    public static final String EMAIL_PLACEMENT = "placements@gcet.edu.in";
    public static final String URL_GCTC_PORTAL = "https://www.gctcportal.in/";
    public static final String URL_OFFICIAL = "https://gcet.edu.in/";
    public static final String URL_GROUP = "https://geethanjaliinstitutions.com/";
    public static final String URL_JNTUH = "https://jntuh.ac.in/";

    private static final List<GctcAnnouncement> VERIFIED_ANNOUNCEMENTS = List.of(
            new GctcAnnouncement(
                    "ann-01",
                    "B.Tech, MBA & M.Tech Autonomous Revaluation Examination Results Published",
                    "28 Oct 2024",
                    "EXAMINATION",
                    "Autonomous examination revaluation outcomes for B.Tech II & III Year (AR20, AR22 Regulations) and PG programs have been finalized and uploaded to the GCTC examination database.",
                    "https://www.gctcportal.in/",
                    true,
                    "Revaluation_Results_Notification_Oct24.pdf",
                    "Controller of Examinations, GCET"
            ),
            new GctcAnnouncement(
                    "ann-02",
                    "IV B.Tech I Semester Mid-I Examination Timetables & CBT Schedules Released",
                    "24 Oct 2024",
                    "EXAMINATION",
                    "Detailed session schedules, room allotments, and Computer Based Test (CBT) slot timings for IV B.Tech I Semester students across all branches are now active.",
                    "https://www.gctcportal.in/",
                    true,
                    "IV_BTech_Mid1_Timetable_Schedule.pdf",
                    "Academic & Examination Section"
            ),
            new GctcAnnouncement(
                    "ann-03",
                    "Autonomous Regular & Supplementary Examination Fee Circular (AR20, AR22, AR24)",
                    "18 Oct 2024",
                    "EXAMINATION",
                    "Notification regarding payment of examination fees for upcoming Autonomous Odd Semester End Examinations. Online payment enabled via State Bank Collect gateway.",
                    "https://www.gctcportal.in/",
                    false,
                    "Exam_Fee_Circular_Autonomous_2024.pdf",
                    "Office of the Principal"
            ),
            new GctcAnnouncement(
                    "ann-04",
                    "TCS National Qualifier Test (NQT) & Cognizant GenC Campus Hiring Drive",
                    "15 Oct 2024",
                    "PLACEMENTS",
                    "Career Development Center (CDC) alerts final year B.Tech students regarding registration guidelines, eligibility criteria, and preparation roadmap for mass IT hiring drives.",
                    "https://gcet.edu.in/",
                    true,
                    "TCS_NQT_Drive_Guidelines_2025Batch.pdf",
                    "Head - Training & Placements (CDC)"
            ),
            new GctcAnnouncement(
                    "ann-05",
                    "BHASWARA 2025: National Level Technical Symposium - Call for Papers & Projects",
                    "10 Oct 2024",
                    "FESTIVAL",
                    "Geethanjali College of Engineering and Technology announces annual national technical symposium BHASWARA 2025. Tracks include AI/ML, Embedded Systems, VLSI, Robotics, and Civil/Mech innovation.",
                    "https://www.gctcportal.in/",
                    false,
                    "Bhaswara_2025_Call_For_Papers.pdf",
                    "Convener - Bhaswara 2025"
            ),
            new GctcAnnouncement(
                    "ann-06",
                    "III B.Tech II Semester Complete Autonomous Results Declared",
                    "05 Oct 2024",
                    "EXAMINATION",
                    "Students can check their semester grade cards, SGPA, and cumulative CGPA on the GCTC Portal by entering their 10-digit roll number.",
                    "https://www.gctcportal.in/",
                    false,
                    "III_BTech_IISem_Autonomous_Results.pdf",
                    "Exam Branch GCET"
            ),
            new GctcAnnouncement(
                    "ann-07",
                    "Autonomous Academic Calendar for Academic Year 2024-2025 (B.Tech & M.Tech)",
                    "22 Sep 2024",
                    "ACADEMIC",
                    "Approved academic schedule detailing semester commencement dates, internal assessment weeks, festival vacations, preparatory holidays, and semester end theory/practical exams.",
                    "https://gcet.edu.in/",
                    false,
                    "Academic_Calendar_2024_2025_Approved.pdf",
                    "Dean - Academics"
            ),
            new GctcAnnouncement(
                    "ann-08",
                    "B.Tech Special One Time Chance (OTC) Supplementary Examination Notification",
                    "19 Sep 2024",
                    "EXAMINATION",
                    "One Time Chance opportunity notification for eligible former students under autonomous and affiliated regulations as per JNTUH institutional guidelines.",
                    "https://www.gctcportal.in/",
                    false,
                    "OTC_Examination_Notification.pdf",
                    "Exam Branch GCET"
            )
    );

    private static final List<ExamResultItem> VERIFIED_RESULTS = List.of(
            new ExamResultItem(
                    "res-01",
                    "B.Tech, MBA & M.Tech Revaluation Results",
                    "July / August 2024",
                    "28 Oct 2024",
                    List.of("AR20", "AR22"),
                    List.of("CSE", "CSE-AIML", "CSE-DS", "ECE", "EEE", "MECH", "CIVIL", "MBA"),
                    "https://www.gctcportal.in/",
                    "REVALUATION"
            ),
            new ExamResultItem(
                    "res-02",
                    "III B.Tech II Semester Regular Examinations Complete Results",
                    "May / June 2024",
                    "05 Oct 2024",
                    List.of("AR20", "AR22"),
                    List.of("All B.Tech Engineering Branches"),
                    "https://www.gctcportal.in/",
                    "REGULAR"
            ),
            new ExamResultItem(
                    "res-03",
                    "IV B.Tech II Semester Advanced Supplementary Results",
                    "August 2024",
                    "28 Sep 2024",
                    List.of("AR20"),
                    List.of("All Final Year Branches"),
                    "https://www.gctcportal.in/",
                    "SUPPLEMENTARY"
            ),
            new ExamResultItem(
                    "res-04",
                    "B.Tech One Time Chance (OTC) Special Examination Results",
                    "September 2024",
                    "19 Sep 2024",
                    List.of("AR16", "AR18", "AR20"),
                    List.of("Designated Autonomous & Affiliated Batches"),
                    "https://www.gctcportal.in/",
                    "OTC"
            ),
            new ExamResultItem(
                    "res-05",
                    "II B.Tech II Semester Regular & Supplementary Results",
                    "July 2024",
                    "02 Sep 2024",
                    List.of("AR22"),
                    List.of("CSE, CSE-AIML, CSE-DS, CSE-CS, CSE-IoT, ECE, EEE, MECH, CIVIL"),
                    "https://www.gctcportal.in/",
                    "REGULAR"
            )
    );

    private static final List<CollegeClub> VERIFIED_CLUBS = List.of(
            new CollegeClub(
                    "club-stuvibe",
                    "StuVibe",
                    "StuVibe",
                    "CULTURAL",
                    "The premier student-driven campus life, magazine, and cultural pulse of Geethanjali. StuVibe captures student viewpoints, podcast dialogues, and campus milestones.",
                    List.of("Campus Lifestyle Magazine", "Student Talk Series", "Open Mic Sessions", "Creative Writing Competitions"),
                    "Dr. V. S. Triveni (Humanities & Sciences)",
                    "Ananya Rao (IV CSE)",
                    "https://www.gctcportal.in/",
                    "Campus Pulse",
                    "#32C5F4"
            ),
            new CollegeClub(
                    "club-alumni",
                    "GCET Alumni Association",
                    "Alumni",
                    "ALUMNI",
                    "The official alumni chapter connecting thousands of Geethanjali engineering graduates worldwide across Silicon Valley, Europe, and India. Facilitates mentorship, career guidance, and campus networking.",
                    List.of("Annual Alumni Homecoming", "Senior-Junior Industry Mentorship", "Alumni Mock Interview Week", "Scholarship Grants"),
                    "Prof. K. Somasekhar Rao",
                    "Karthik Varma (Alumni Coordinator)",
                    "https://www.gctcportal.in/",
                    "Global Network",
                    "#2684FF"
            ),
            new CollegeClub(
                    "club-dopy",
                    "DoPY - Department of Photography",
                    "DoPY",
                    "MEDIA",
                    "The official photography and cinematic media fraternity of GCET. DoPY documents institutional fests, cultural nights, sports tournaments, and produces cinematic recaps.",
                    List.of("College Fest Media Coverage", "Annual Photography Exhibition", "Cinematography Workshops", "Official Photo Archive"),
                    "Mr. P. Srikanth (IT)",
                    "Rohit Kulkarni (III ECE)",
                    "https://www.gctcportal.in/",
                    "Visual Media",
                    "#F59E0B"
            ),
            new CollegeClub(
                    "club-nen",
                    "NEN - National Entrepreneurship Network (GCET E-Cell)",
                    "NEN",
                    "ENTREPRENEURSHIP",
                    "The flagship entrepreneurship cell affiliated with National Entrepreneurship Network (NEN). Fosters innovation, startup ideation, venture pitching, and business incubations.",
                    List.of("E-Summit & Startup Pitch Fest", "Ideation Bootcamps", "Angel Investor Roundtables", "Intellectual Property & Patent Workshops"),
                    "Dr. G. Neeraja Rani (MBA / Management)",
                    "Sahithi Reddy (IV CSE-AIML)",
                    "https://www.gctcportal.in/",
                    "Innovation Cell",
                    "#10B981"
            ),
            new CollegeClub(
                    "club-cdc",
                    "Career Development Center (CDC)",
                    "CDC",
                    "CAREER",
                    "The core placement and corporate readiness wing at GCET. Conducts continuous soft skill bootcamps, technical coding sprints, resume audits, and mock interview drives.",
                    List.of("Corporate Readiness Program (CRP)", "Aptitude & Coding Mock Contests", "Company-Specific Training Modules", "Alumni Career Talks"),
                    "Prof. B. S. Shanthi (Director - Training & Placement)",
                    "Varun Teja (IV ECE)",
                    "https://www.gctcportal.in/",
                    "Placement Wing",
                    "#6366F1"
            ),
            new CollegeClub(
                    "club-deco",
                    "DECO - Design & Creative Operations",
                    "DECO",
                    "MEDIA",
                    "The creative backbone of GCET responsible for visual identities, UI/UX designing, festival stage scenography, digital banners, and web portal aesthetics.",
                    List.of("Fest Branding & Visual Identity", "UI/UX Design Jams", "Stage Decor & Ambient Scenography", "Motion Graphics Production"),
                    "Mrs. Swathi Krishna (CSE)",
                    "Deepak Kumar (III CSE)",
                    "https://www.gctcportal.in/",
                    "Creative Crew",
                    "#EC4899"
            ),
            new CollegeClub(
                    "club-gshf",
                    "GSHF - Geethanjali Students Helping Fraternity",
                    "GSHF",
                    "SOCIAL_SERVICE",
                    "A student-driven humanitarian service organization fostering community empowerment, blood donation drives, educational support for rural schools, and disaster relief.",
                    List.of("Annual Mega Blood Donation Camp", "Rural School Teaching Drives (Cheeryal & Keesara)", "Environmental Tree Plantation Drives", "Medical Health Checkup Camps"),
                    "Dr. M. Ashok Kumar (Dean - Student Affairs)",
                    "Sneha Murali (IV CIVIL)",
                    "https://www.gctcportal.in/",
                    "Social Impact",
                    "#EF4444"
            ),
            new CollegeClub(
                    "club-srujanastra",
                    "Srujanastra - Technical Innovation Club",
                    "Srujanastra",
                    "TECHNICAL",
                    "The premier technical club fostering cutting-edge hardware, IoT, embedded robotics, open-source software development, and national hackathon participation.",
                    List.of("24-Hour Hackathons", "Robotics Line-Follower & Maze Challenges", "IoT Smart Campus Initiatives", "Open Source Project Showcase"),
                    "Dr. K. Srinivas (CSE)",
                    "Aditya Prasad (IV CSE-IoT)",
                    "https://www.gctcportal.in/",
                    "Tech & Hackathons",
                    "#06B6D4"
            )
    );

    private static final List<CollegeFestival> VERIFIED_FESTIVALS = List.of(
            new CollegeFestival(
                    "fest-vibgyor",
                    "VIBGYOR",
                    "Colors of Youth, Symphony of Talent",
                    "CULTURAL",
                    "The annual flagship national cultural extravaganza of Geethanjali College of Engineering and Technology. VIBGYOR brings together thousands of students from over 100 colleges for high-energy music, dance, battle of bands, fashion runway, and celebrity concerts.",
                    List.of("National Inter-Collegiate Dance Championship", "Battle of the Bands", "Fashion Walk & Traditional Gala", "Celebrity Star Night & DJ Showcase", "Fine Arts & Photography Exhibition"),
                    "March (Annual)",
                    List.of("All Academic Departments", "StuVibe", "DoPY", "DECO"),
                    "https://www.gctcportal.in/"
            ),
            new CollegeFestival(
                    "fest-bhaswara",
                    "BHASWARA",
                    "Igniting Engineering Minds & Technical Prowess",
                    "TECHNICAL_SYMPOSIUM",
                    "GCET's prestigious annual National Level Technical Symposium. BHASWARA provides a competitive platform for emerging engineers to present peer-reviewed research papers, working hardware prototypes, algorithmic coding solutions, and tech debates.",
                    List.of("National Paper Presentation Competition", "Live Hardware Project Expo", "Speed Coding & Bug Hunt Challenge", "Technical Quiz & CAD Championship", "Keynotes by Eminent Scientists & Tech Leaders"),
                    "October / November",
                    List.of("CSE", "CSE-AIML", "CSE-DS", "IT", "ECE", "EEE", "MECH", "CIVIL"),
                    "https://www.gctcportal.in/"
            ),
            new CollegeFestival(
                    "fest-electrika",
                    "ELECTRIKA",
                    "Unleashing Electrical & Electronics Innovation",
                    "DEPARTMENTAL_TECH_FEST",
                    "The flagship technical fiesta orchestrated by the Department of Electrical & Electronics Engineering (EEE) and Electronics & Communication Engineering (ECE). Highlights power systems, EV powertrains, robotics, microcontrollers, and VLSI circuit design.",
                    List.of("Robo-Wars & Line Tracer Arena", "PCB Circuit Designing & Soldering Sprint", "Solar & Renewable Energy Expo", "Smart Grid Technical Paper Contest", "Drone Flight & Obstacle Navigation"),
                    "December",
                    List.of("Electrical and Electronics Engineering (EEE)", "Electronics and Communication (ECE)"),
                    "https://www.gctcportal.in/"
            ),
            new CollegeFestival(
                    "fest-manotron",
                    "MANOTRON",
                    "Where Mechanical Mechanics Meets Mechatronics Automation",
                    "DEPARTMENTAL_TECH_FEST",
                    "The premier mechanical, mechatronics, and manufacturing automation showcase at GCET. Features automated design sprints, 3D CAD modeling, IC engine assembly challenges, and automated vehicle demonstrations.",
                    List.of("Go-Kart & All-Terrain Vehicle Showcase", "3D CAD / SolidWorks Rapid Modeling Contest", "Lathe Machine & CNC Precision Machining Contest", "Water Rocketry Championship", "Industrial Automation & PLC Workshops"),
                    "February",
                    List.of("Mechanical Engineering (MECH)", "Civil Engineering (CIVIL)"),
                    "https://www.gctcportal.in/"
            )
    );

    private static final List<StudentResource> VERIFIED_RESOURCES = List.of(
            new StudentResource("res-seating", "Examination Seating Plan",
                    "Look up daily examination hall allotments, bench numbers, and room allocation for Mid and Semester End Examinations.",
                    "EXAM_DESK", "https://www.gctcportal.in/", "View Seating Plan", true),
            new StudentResource("res-digilib", "Digital Library & DSpace Portal",
                    "Access IEEE Xplore, DELNET, NPTEL local mirror lectures, institutional e-books, previous question papers, and research papers.",
                    "LIBRARY", "https://gcet.edu.in/", "Access Digital Library", false),
            new StudentResource("res-resume", "GCET Placement Resume Generator",
                    "Build standardized, ATS-compliant resumes formatted to GCET Training and Placement Cell corporate criteria.",
                    "CAREER_TOOLS", "https://www.gctcportal.in/", "Launch Resume Builder", false),
            new StudentResource("res-libzone", "Library Zone & OPAC System",
                    "Online Public Access Catalog (OPAC) to search 50,000+ technical volumes, book availability, reservations, and borrow dates.",
                    "LIBRARY", "https://gcet.edu.in/", "Search Library Catalog", false),
            new StudentResource("res-calendar", "Academic Calendar (AR20, AR22, AR24)",
                    "Official schedule for B.Tech, M.Tech, and MBA programs detailing instructional days, holidays, mid examinations, and semester vacations.",
                    "ACADEMIC_CURRICULUM", "https://gcet.edu.in/", "Download Calendar PDF", false),
            new StudentResource("res-syllabus", "Autonomous Syllabus & Regulations",
                    "Detailed course structures, credits, pre-requisites, syllabus units, and reference textbooks for AR20, AR22, and AR24 regulations.",
                    "ACADEMIC_CURRICULUM", "https://gcet.edu.in/", "Browse Branch Syllabi", false),
            new StudentResource("res-fee", "Fee Particulars & SBI Collect",
                    "Official institutional fee schedules (tuition, transport, examination, and hostel) with direct link to SBI Collect gateway.",
                    "FEE_PORTAL", "https://gcet.edu.in/", "Fee Portal & Online Pay", false),
            new StudentResource("res-intake", "Approved Branch Intake Matrix",
                    "AICTE and JNTUH approved student seat intake across B.Tech engineering branches and postgraduate MBA/M.Tech programs.",
                    "ACADEMIC_CURRICULUM", "https://gcet.edu.in/", "View Approved Intake", false),
            new StudentResource("res-overview", "Autonomous Academic Overview",
                    "Credit framework, relative/absolute grading scale (O, A+, A, B+, B, C, F), minimum attendance rules (75%), and promotion guidelines.",
                    "ACADEMIC_CURRICULUM", "https://gcet.edu.in/", "Read Academic Guidelines", false)
    );

    private static final List<BranchIntake> GCET_APPROVED_INTAKE = List.of(
            new BranchIntake("CSE", "Computer Science and Engineering", "B.Tech", 240, 2005),
            new BranchIntake("CSE-AIML", "CSE (Artificial Intelligence & Machine Learning)", "B.Tech", 180, 2020),
            new BranchIntake("CSE-DS", "CSE (Data Science)", "B.Tech", 120, 2020),
            new BranchIntake("CSE-CS", "CSE (Cyber Security)", "B.Tech", 60, 2021),
            new BranchIntake("CSE-IoT", "CSE (Internet of Things)", "B.Tech", 60, 2021),
            new BranchIntake("IT", "Information Technology", "B.Tech", 120, 2005),
            new BranchIntake("ECE", "Electronics and Communication Engineering", "B.Tech", 180, 2005),
            new BranchIntake("EEE", "Electrical and Electronics Engineering", "B.Tech", 60, 2005),
            new BranchIntake("MECH", "Mechanical Engineering", "B.Tech", 60, 2005),
            new BranchIntake("CIVIL", "Civil Engineering", "B.Tech", 60, 2011),
            new BranchIntake("MBA", "Master of Business Administration", "MBA", 60, 2007)
    );

    public PortalSyncStatus getSyncStatus(boolean refreshedNow) {
        String ts = refreshedNow
                ? LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + " IST (Just now)"
                : "28 Oct 2024, 18:30 IST";
        return new PortalSyncStatus(
                true,
                ts,
                URL_GCTC_PORTAL,
                URL_OFFICIAL,
                "GCTC Portal & GCET Exam Branch Registry",
                "Official records verified from GCTC Portal (Cheeryal, Keesara)"
        );
    }

    public List<GctcAnnouncement> getAnnouncements(String category, String searchQuery) {
        String cat = category == null ? "ALL" : category.trim().toUpperCase(Locale.ROOT);
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        return VERIFIED_ANNOUNCEMENTS.stream()
                .filter(a -> "ALL".equals(cat) || a.category().equalsIgnoreCase(cat))
                .filter(a -> q.isEmpty()
                        || a.title().toLowerCase(Locale.ROOT).contains(q)
                        || a.summary().toLowerCase(Locale.ROOT).contains(q)
                        || a.postedBy().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public List<ExamResultItem> getResults() {
        return VERIFIED_RESULTS;
    }

    public List<CollegeClub> getClubs() {
        return VERIFIED_CLUBS;
    }

    public List<CollegeFestival> getFestivals() {
        return VERIFIED_FESTIVALS;
    }

    public List<StudentResource> getResources() {
        return VERIFIED_RESOURCES;
    }

    public List<BranchIntake> getBranchIntake() {
        return GCET_APPROVED_INTAKE;
    }

    public int getTotalIntake() {
        return GCET_APPROVED_INTAKE.stream().mapToInt(BranchIntake::sanctionedIntake).sum();
    }
}
