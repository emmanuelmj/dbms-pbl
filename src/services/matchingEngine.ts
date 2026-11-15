import { Student, CompanyMatch, PlacementDrive, Company, Skill } from '../types';
import { eligibilityService } from './eligibilityService';

export const matchingEngine = {
  findMatchesForStudent(
    student: Student,
    allDrives: PlacementDrive[] = [],
    companies: Company[] = [],
    allSkills: Skill[] = []
  ): CompanyMatch[] {
    const drives = allDrives.filter((d) => d.status === 'OPEN' || d.status === 'UPCOMING');

    const matches: CompanyMatch[] = [];

    drives.forEach((drive) => {
      const company = companies.find((c) => c.id === drive.companyId) || {
        id: drive.companyId,
        name: drive.companyName,
        industry: 'Information Technology',
        website: '#',
        location: 'Hyderabad',
        description: '',
        hrName: '',
        hrEmail: '',
        hrPhone: '',
        tier: 'Standard IT' as const,
        totalDrivesConducted: 1,
        createdAt: new Date().toISOString(),
      };

      const eligibility = eligibilityService.checkEligibility(student, drive, allSkills);

      // Analyze skill overlap
      const skillsMatched: string[] = [];
      const skillsMissing: string[] = [];

      drive.requiredSkills.forEach((req) => {
        const sMeta = allSkills.find((s) => s.id === req.skillId);
        const name = sMeta ? sMeta.name : req.skillId;
        const studentSkill = student.skills.find((s) => s.skillId === req.skillId);
        if (studentSkill && studentSkill.proficiency >= req.minProficiency) {
          skillsMatched.push(name);
        } else {
          skillsMissing.push(name);
        }
      });

      // Transparent explanation
      let explanation = '';
      if (eligibility.isEligible) {
        explanation = `Eligible Candidate (${eligibility.score}% match): Meets CGPA cutoff (${student.cgpa} >= ${drive.minCgpa}), branch (${student.branch}) matches, and satisfied ${skillsMatched.length} of ${drive.requiredSkills.length} required skills.`;
      } else {
        const primaryReason = eligibility.reasons[0] || 'Unmet drive criteria';
        explanation = `Ineligible for direct application: ${primaryReason}. ${skillsMissing.length > 0 ? `Skills to bridge: ${skillsMissing.slice(0, 2).join(', ')}.` : ''}`;
      }

      matches.push({
        drive,
        company,
        matchScore: eligibility.score,
        eligibility,
        skillsMatched,
        skillsMissing,
        explanation,
      });
    });

    // Sort descending by match score, with eligible drives prioritized
    return matches.sort((a, b) => {
      if (a.eligibility.isEligible !== b.eligibility.isEligible) {
        return a.eligibility.isEligible ? -1 : 1;
      }
      return b.matchScore - a.matchScore;
    });
  },
};
