import { Student, PlacementDrive, EligibilityResult, SkillProficiency, Skill } from '../types';

export const eligibilityService = {
  checkEligibility(student: Student, drive: PlacementDrive, allSkills: Skill[] = []): EligibilityResult {
    const reasons: string[] = [];

    // 1. CGPA Check
    const cgpaPassed = student.cgpa >= drive.minCgpa;
    if (!cgpaPassed) {
      reasons.push(
        `CGPA ${student.cgpa.toFixed(2)} is below minimum required CGPA of ${drive.minCgpa.toFixed(2)}`
      );
    }

    // 2. Branch Eligibility Check
    const branchPassed = drive.eligibleBranches.includes(student.branch);
    if (!branchPassed) {
      reasons.push(
        `Branch "${student.branch}" is not among eligible branches (${drive.eligibleBranches.join(', ')})`
      );
    }

    // 3. Graduation Year Check
    const gradYearPassed = student.graduationYear === drive.targetGraduationYear;
    if (!gradYearPassed) {
      reasons.push(
        `Graduation year ${student.graduationYear} does not match required passing batch of ${drive.targetGraduationYear}`
      );
    }

    // 4. Backlog Check
    const backlogsPassed = student.backlogs <= drive.maxBacklogsAllowed;
    if (!backlogsPassed) {
      reasons.push(
        `Student has ${student.backlogs} active backlog(s); drive strictly permits maximum of ${drive.maxBacklogsAllowed}`
      );
    }

    // 5. Skills & Proficiency Requirements
    let satisfiedSkillsCount = 0;
    const skillDetails = drive.requiredSkills.map((req) => {
      const skillMeta = allSkills.find((s) => s.id === req.skillId);
      const studentSkill = student.skills.find((s) => s.skillId === req.skillId);
      const studentProf: SkillProficiency | 0 = studentSkill ? studentSkill.proficiency : 0;
      const isSatisfied = studentProf >= req.minProficiency;

      if (isSatisfied) {
        satisfiedSkillsCount++;
      } else {
        const skillName = skillMeta ? skillMeta.name : req.skillId;
        if (req.isMandatory) {
          if (studentProf === 0) {
            reasons.push(`Missing mandatory skill: ${skillName} (Requires Level ${req.minProficiency})`);
          } else {
            reasons.push(
              `Skill ${skillName} level ${studentProf} is below required proficiency level ${req.minProficiency}`
            );
          }
        }
      }

      return {
        skillId: req.skillId,
        skillName: skillMeta ? skillMeta.name : 'Unknown Skill',
        studentProficiency: studentProf,
        requiredProficiency: req.minProficiency,
        satisfied: isSatisfied,
        isMandatory: req.isMandatory,
        gap: Math.max(0, req.minProficiency - studentProf),
      };
    });

    const mandatorySkillsSatisfied = skillDetails
      .filter((s) => s.isMandatory)
      .every((s) => s.satisfied);

    const isEligible =
      cgpaPassed &&
      branchPassed &&
      gradYearPassed &&
      backlogsPassed &&
      mandatorySkillsSatisfied;

    // Calculate quantitative eligibility & preparedness score (0 - 100)
    let score = 0;
    if (cgpaPassed) score += 30;
    else score += Math.max(0, (student.cgpa / drive.minCgpa) * 20);

    if (branchPassed) score += 20;
    if (gradYearPassed) score += 10;
    if (backlogsPassed) score += 10;

    const totalSkills = drive.requiredSkills.length;
    if (totalSkills > 0) {
      const skillRatio = satisfiedSkillsCount / totalSkills;
      score += Math.round(skillRatio * 30);
    } else {
      score += 30;
    }

    score = Math.min(100, Math.max(0, Math.round(score)));

    return {
      isEligible,
      score,
      criteriaChecks: {
        cgpa: {
          satisfied: cgpaPassed,
          studentVal: student.cgpa,
          requiredVal: drive.minCgpa,
          message: cgpaPassed
            ? `CGPA ${student.cgpa.toFixed(2)} meets criteria (>= ${drive.minCgpa.toFixed(2)})`
            : `CGPA ${student.cgpa.toFixed(2)} is below minimum requirement (${drive.minCgpa.toFixed(2)})`,
        },
        branch: {
          satisfied: branchPassed,
          studentVal: student.branch,
          allowedBranches: drive.eligibleBranches,
          message: branchPassed
            ? `Branch ${student.branch} is eligible`
            : `Branch ${student.branch} is not eligible for this role`,
        },
        graduationYear: {
          satisfied: gradYearPassed,
          studentVal: student.graduationYear,
          requiredVal: drive.targetGraduationYear,
          message: gradYearPassed
            ? `Graduation year ${student.graduationYear} matches`
            : `Graduation year must be ${drive.targetGraduationYear}`,
        },
        backlogs: {
          satisfied: backlogsPassed,
          studentVal: student.backlogs,
          maxAllowed: drive.maxBacklogsAllowed,
          message: backlogsPassed
            ? `Backlogs (${student.backlogs}) within permissible limit (${drive.maxBacklogsAllowed})`
            : `Exceeds maximum allowable backlogs (${drive.maxBacklogsAllowed})`,
        },
        skills: {
          satisfied: mandatorySkillsSatisfied,
          totalRequired: totalSkills,
          satisfiedCount: satisfiedSkillsCount,
          details: skillDetails,
          message: mandatorySkillsSatisfied
            ? `All mandatory skills satisfied (${satisfiedSkillsCount}/${totalSkills} total met)`
            : `Missing or insufficient proficiency in mandatory skills`,
        },
      },
      reasons,
    };
  },
};
