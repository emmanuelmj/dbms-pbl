// Official GCET Campus Building Photograph (uploaded by institution - arrow-free clean campus view)
export const GCET_CAMPUS_BUILDING_PHOTO = '/assets/aistudio/gcet_campus_clean.png';

// Official Geethanjali College of Engineering and Technology (GCET) Institutional Logo
export const GCET_OFFICIAL_LOGO = '/assets/gcet_logo.png';

// High-fidelity architectural vector backdrops representing GCET Cheeryal Campus
export const GCET_CAMPUS_DAY = `data:image/svg+xml;utf8,${encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 700" width="100%" height="100%">
  <defs>
    <linearGradient id="skyDay" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#38bdf8"/>
      <stop offset="60%" stop-color="#bae6fd"/>
      <stop offset="100%" stop-color="#e0f2fe"/>
    </linearGradient>
    <linearGradient id="glassFacade" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0284c7"/>
      <stop offset="50%" stop-color="#38bdf8"/>
      <stop offset="100%" stop-color="#0369a1"/>
    </linearGradient>
    <linearGradient id="buildingPillar" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#e2e8f0"/>
      <stop offset="50%" stop-color="#f8fafc"/>
      <stop offset="100%" stop-color="#cbd5e1"/>
    </linearGradient>
    <linearGradient id="lawn" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#22c55e"/>
      <stop offset="100%" stop-color="#15803d"/>
    </linearGradient>
  </defs>
  <!-- Sky -->
  <rect width="1600" height="700" fill="url(#skyDay)"/>
  <!-- Sun & Clouds -->
  <circle cx="1350" cy="120" r="60" fill="#fef08a" opacity="0.8"/>
  <ellipse cx="320" cy="110" rx="120" ry="35" fill="#ffffff" opacity="0.6"/>
  <ellipse cx="400" cy="100" rx="140" ry="40" fill="#ffffff" opacity="0.7"/>
  <ellipse cx="980" cy="140" rx="160" ry="35" fill="#ffffff" opacity="0.5"/>
  <!-- Distant Hilltops (Cheeryal Valley) -->
  <path d="M0,450 Q300,380 600,420 T1200,400 Q1450,410 1600,440 L1600,700 L0,700 Z" fill="#94a3b8" opacity="0.5"/>
  <!-- Main Academic Block A - Geethanjali Engineering -->
  <rect x="250" y="240" width="1100" height="280" rx="8" fill="#f1f5f9"/>
  <!-- Central Atrium Glass Facade -->
  <rect x="580" y="210" width="440" height="310" rx="6" fill="url(#glassFacade)"/>
  <!-- Glass Window Panes Grid -->
  <g stroke="#ffffff" stroke-width="2" opacity="0.4">
    <line x1="580" y1="260" x2="1020" y2="260"/>
    <line x1="580" y1="310" x2="1020" y2="310"/>
    <line x1="580" y1="360" x2="1020" y2="360"/>
    <line x1="580" y1="410" x2="1020" y2="410"/>
    <line x1="580" y1="460" x2="1020" y2="460"/>
    <line x1="640" y1="210" x2="640" y2="520"/>
    <line x1="700" y1="210" x2="700" y2="520"/>
    <line x1="760" y1="210" x2="760" y2="520"/>
    <line x1="840" y1="210" x2="840" y2="520"/>
    <line x1="900" y1="210" x2="900" y2="520"/>
    <line x1="960" y1="210" x2="960" y2="520"/>
  </g>
  <!-- Institutional Header Arch Ribbon -->
  <rect x="560" y="180" width="480" height="40" rx="4" fill="#101a35"/>
  <text x="800" y="206" font-family="'Segoe UI', Arial, sans-serif" font-size="14" font-weight="bold" fill="#38bdf8" text-anchor="middle" letter-spacing="2">GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY</text>
  <!-- Architectural Support Columns -->
  <rect x="280" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <rect x="360" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <rect x="440" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <rect x="520" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <rect x="1060" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <rect x="1140" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <rect x="1220" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <rect x="1300" y="250" width="22" height="270" fill="url(#buildingPillar)"/>
  <!-- Left & Right Wing Classrooms Windows -->
  <g fill="#93c5fd" opacity="0.6">
    <rect x="310" y="280" width="40" height="50" rx="3"/><rect x="390" y="280" width="40" height="50" rx="3"/><rect x="470" y="280" width="40" height="50" rx="3"/>
    <rect x="310" y="350" width="40" height="50" rx="3"/><rect x="390" y="350" width="40" height="50" rx="3"/><rect x="470" y="350" width="40" height="50" rx="3"/>
    <rect x="310" y="420" width="40" height="50" rx="3"/><rect x="390" y="420" width="40" height="50" rx="3"/><rect x="470" y="420" width="40" height="50" rx="3"/>
    <rect x="1090" y="280" width="40" height="50" rx="3"/><rect x="1170" y="280" width="40" height="50" rx="3"/><rect x="1250" y="280" width="40" height="50" rx="3"/>
    <rect x="1090" y="350" width="40" height="50" rx="3"/><rect x="1170" y="350" width="40" height="50" rx="3"/><rect x="1250" y="350" width="40" height="50" rx="3"/>
    <rect x="1090" y="420" width="40" height="50" rx="3"/><rect x="1170" y="420" width="40" height="50" rx="3"/><rect x="1250" y="420" width="40" height="50" rx="3"/>
  </g>
  <!-- Landscaped Front Courtyard & Green Lawn -->
  <path d="M0,510 L1600,510 L1600,700 L0,700 Z" fill="url(#lawn)"/>
  <!-- Paved Walkway -->
  <polygon points="800,510 740,700 860,700" fill="#e2e8f0"/>
  <!-- Trees & Greenery Along Campus -->
  <circle cx="210" cy="510" r="45" fill="#15803d"/><circle cx="170" cy="520" r="35" fill="#16a34a"/>
  <circle cx="1390" cy="510" r="45" fill="#15803d"/><circle cx="1430" cy="520" r="35" fill="#16a34a"/>
</svg>
`)}`;

export const GCET_CAMPUS_NIGHT = `data:image/svg+xml;utf8,${encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 700" width="100%" height="100%">
  <defs>
    <linearGradient id="skyNight" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#050a18"/>
      <stop offset="50%" stop-color="#0b1329"/>
      <stop offset="100%" stop-color="#101a35"/>
    </linearGradient>
    <linearGradient id="nightGlass" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0f172a"/>
      <stop offset="50%" stop-color="#1e293b"/>
      <stop offset="100%" stop-color="#0f172a"/>
    </linearGradient>
    <linearGradient id="nightLawn" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#064e3b"/>
      <stop offset="100%" stop-color="#022c22"/>
    </linearGradient>
  </defs>
  <!-- Night Sky -->
  <rect width="1600" height="700" fill="url(#skyNight)"/>
  <!-- Stars -->
  <g fill="#ffffff" opacity="0.7">
    <circle cx="120" cy="80" r="1.5"/><circle cx="280" cy="50" r="1.2"/><circle cx="450" cy="90" r="1.5"/>
    <circle cx="700" cy="40" r="1.5"/><circle cx="950" cy="70" r="1.8"/><circle cx="1180" cy="55" r="1.2"/>
    <circle cx="1400" cy="90" r="1.5"/><circle cx="1520" cy="60" r="1.5"/><circle cx="850" cy="110" r="1.2"/>
  </g>
  <!-- Crescent Moon -->
  <circle cx="1380" cy="110" r="38" fill="#fef08a" opacity="0.9"/>
  <circle cx="1395" cy="105" r="34" fill="#0b1329"/>
  <!-- Distant Hilltops -->
  <path d="M0,450 Q300,380 600,420 T1200,400 Q1450,410 1600,440 L1600,700 L0,700 Z" fill="#020617" opacity="0.8"/>
  <!-- Main Academic Block - Illuminated at Night -->
  <rect x="250" y="240" width="1100" height="280" rx="8" fill="#0f172a"/>
  <!-- Central Atrium with Warm Lights -->
  <rect x="580" y="210" width="440" height="310" rx="6" fill="url(#nightGlass)"/>
  <!-- Illuminated Interior Windows (Gold/Amber) -->
  <g fill="#fde047" opacity="0.8">
    <rect x="610" y="250" width="30" height="40" rx="2"/><rect x="670" y="250" width="30" height="40" rx="2"/><rect x="730" y="250" width="30" height="40" rx="2"/>
    <rect x="830" y="250" width="30" height="40" rx="2"/><rect x="890" y="250" width="30" height="40" rx="2"/><rect x="950" y="250" width="30" height="40" rx="2"/>
    <rect x="610" y="320" width="30" height="40" rx="2"/><rect x="670" y="320" width="30" height="40" rx="2"/><rect x="730" y="320" width="30" height="40" rx="2"/>
    <rect x="830" y="320" width="30" height="40" rx="2"/><rect x="890" y="320" width="30" height="40" rx="2"/><rect x="950" y="320" width="30" height="40" rx="2"/>
    <rect x="610" y="390" width="30" height="40" rx="2"/><rect x="670" y="390" width="30" height="40" rx="2"/><rect x="730" y="390" width="30" height="40" rx="2"/>
    <rect x="830" y="390" width="30" height="40" rx="2"/><rect x="890" y="390" width="30" height="40" rx="2"/><rect x="950" y="390" width="30" height="40" rx="2"/>
  </g>
  <!-- Building Header Neon Glow -->
  <rect x="560" y="180" width="480" height="40" rx="4" fill="#090d16" stroke="#2684ff" stroke-width="2"/>
  <text x="800" y="206" font-family="'Segoe UI', Arial, sans-serif" font-size="14" font-weight="bold" fill="#38bdf8" text-anchor="middle" letter-spacing="2">GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY</text>
  <!-- Architectural Pillars -->
  <rect x="280" y="250" width="22" height="270" fill="#1e293b"/>
  <rect x="360" y="250" width="22" height="270" fill="#1e293b"/>
  <rect x="440" y="250" width="22" height="270" fill="#1e293b"/>
  <rect x="520" y="250" width="22" height="270" fill="#1e293b"/>
  <rect x="1060" y="250" width="22" height="270" fill="#1e293b"/>
  <rect x="1140" y="250" width="22" height="270" fill="#1e293b"/>
  <rect x="1220" y="250" width="22" height="270" fill="#1e293b"/>
  <rect x="1300" y="250" width="22" height="270" fill="#1e293b"/>
  <!-- Courtyard & Night Garden -->
  <path d="M0,510 L1600,510 L1600,700 L0,700 Z" fill="url(#nightLawn)"/>
  <!-- Night Lamp Post Glows -->
  <circle cx="720" cy="540" r="18" fill="#fef08a" opacity="0.3"/><circle cx="720" cy="540" r="6" fill="#fef08a"/>
  <circle cx="880" cy="540" r="18" fill="#fef08a" opacity="0.3"/><circle cx="880" cy="540" r="6" fill="#fef08a"/>
</svg>
`)}`;

export const GCET_COLLEGE_DETAILS = {
  name: 'Geethanjali College of Engineering and Technology',
  shortName: 'GCET',
  tagline: 'Your Skills. Your Opportunities. Your Future.',
  autonomousStatus: 'UGC Autonomous Institution · Affiliated to JNTU Hyderabad',
  accreditation: 'Accredited by NAAC with A+ Grade · NBA Accredited Branches',
  address: {
    campus: 'Sy. No. 33 & 34, Cheeryal (V), Keesara (M)',
    district: 'Medchal-Malkajgiri District',
    state: 'Telangana, India',
    pinCode: '501301',
  },
  contacts: {
    frontOffice: '+91 9182058187',
    examBranch: '+91 9182058190',
    academicSection: '+91 9182058194',
    accountsSection: '+91 9182058196 / +91 9182058192',
    transportSection: '+91 9182058186',
    admissionsAO: '+91 9866308271',
    generalEmail: 'info@gcet.edu.in',
    admissionsEmail: 'ao@gcet.edu.in',
    placementEmail: 'placements@gcet.edu.in',
  },
  urls: {
    officialWebsite: 'https://gcet.edu.in/',
    gctcPortal: 'https://www.gctcportal.in/',
    resultsPortal: 'https://www.gctcportal.in/',
    geethanjaliGroup: 'https://geethanjaliinstitutions.com/',
    jntuhWebsite: 'https://jntuh.ac.in/',
  },
};
