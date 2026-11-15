import {
  Student,
  PlacementDrive,
  SkillGapAnalysis,
  SkillGapItem,
  PROFICIENCY_LABELS,
  SkillProficiency,
  Skill,
} from '../types';

// Precise curriculum recommendations based on skill and gap level
function getCurriculumRecommendation(skillName: string, studentProf: number, requiredProf: number): string {
  const normName = skillName.toLowerCase();

  if (studentProf === 0) {
    if (normName.includes('java')) {
      return 'Complete GCET Java Programming Lab: Focus on OOP principles, Polymorphism, Collections Framework (ArrayList, HashMap), and Exception Handling.';
    }
    if (normName.includes('data structures') || normName.includes('dsa')) {
      return 'Practice core DSA modules: Arrays, Linked Lists, Stacks, Queues, Binary Trees, and solve LeetCode Top 50 Easy questions.';
    }
    if (normName.includes('algorithms')) {
      return 'Review algorithmic paradigms: Time/Space Big-O analysis, Sorting algorithms, Binary Search, and Dynamic Programming foundations.';
    }
    if (normName.includes('mysql') || normName.includes('database') || normName.includes('sql')) {
      return 'Study SQL fundamentals: INNER/LEFT JOINs, Subqueries, Normalization (1NF to 3NF), and MySQL transaction isolation.';
    }
    if (normName.includes('react')) {
      return 'Build a portfolio project with React: JSX, useState, useEffect, props passing, and component lifecycle.';
    }
    if (normName.includes('spring boot')) {
      return 'Set up Spring Initializr: Learn Dependency Injection, @RestController, Spring Data JPA with MySQL repository.';
    }
    if (normName.includes('linux')) {
      return 'Learn standard Linux terminal commands: grep, awk, file permissions (chmod), piping, process management (ps, kill), and bash scripts.';
    }
    if (normName.includes('networks')) {
      return 'Review Computer Networks fundamentals: OSI 7-layer model, TCP 3-way handshake, HTTP vs HTTPS, DNS resolution, and IP routing.';
    }
    if (normName.includes('aws') || normName.includes('cloud')) {
      return 'Review AWS Free Tier core services: EC2 virtual machines, S3 bucket storage, and IAM user roles and security groups.';
    }
    if (normName.includes('aptitude')) {
      return 'Practice GCET Placement Cell Aptitude modules: Quantitative Time & Work, Speed & Distance, and Logical Venn Diagrams.';
    }
    return `Enroll in bridge training for ${skillName} to acquire fundamental baseline proficiency.`;
  }

  // Partial gap (student has basic/intermediate but needs higher)
  if (normName.includes('java')) {
    return 'Advance to Java 17 Streams API, Lambdas, Multi-threading (ExecutorService), and JUnit 5 unit testing.';
  }
  if (normName.includes('data structures') || normName.includes('dsa')) {
    return 'Level up to Graph algorithms (Dijkstra, BFS/DFS cycles), Trie data structures, and Disjoint Set Union (DSU).';
  }
  if (normName.includes('algorithms')) {
    return 'Deep dive into 2D Dynamic Programming (Knapsack, LCS), Backtracking, and Greedy scheduling optimizations.';
  }
  if (normName.includes('mysql') || normName.includes('database') || normName.includes('sql')) {
    return 'Master B-Tree indexing, execution plan analysis (EXPLAIN), stored procedures, and deadlock prevention.';
  }
  if (normName.includes('spring boot')) {
    return 'Implement JWT security filter chains, Spring Cloud API gateway, and microservice resilience patterns.';
  }
  if (normName.includes('linux')) {
    return 'Automate repetitive workflows with Shell scripting, systemd service management, and network diagnostic tools (netstat, curl, tcpdump).';
  }
  return `Upskill in ${skillName} from Level ${studentProf} (${PROFICIENCY_LABELS[studentProf as SkillProficiency]}) to target Level ${requiredProf} (${PROFICIENCY_LABELS[requiredProf as SkillProficiency]}).`;
}

export const skillGapService = {
  analyzeSkillGap(student: Student, drive: PlacementDrive, allSkills: Skill[] = []): SkillGapAnalysis {
    const skillsAnalysis: SkillGapItem[] = [];

    let satisfiedCount = 0;
    let partialCount = 0;
    let missingCount = 0;

    drive.requiredSkills.forEach((req) => {
      const skillObj = allSkills.find((s) => s.id === req.skillId);
      const studentSkill = student.skills.find((s) => s.skillId === req.skillId);
      const studentProf = (studentSkill ? studentSkill.proficiency : 0) as SkillProficiency | 0;

      let status: 'MET' | 'PARTIAL' | 'MISSING' = 'MISSING';
      let gapLevel = 0;

      if (studentProf >= req.minProficiency) {
        status = 'MET';
        satisfiedCount++;
      } else if (studentProf > 0) {
        status = 'PARTIAL';
        partialCount++;
        gapLevel = req.minProficiency - studentProf;
      } else {
        status = 'MISSING';
        missingCount++;
        gapLevel = req.minProficiency;
      }

      const skillName = skillObj ? skillObj.name : 'Unknown Skill';
      const category = skillObj ? skillObj.category : 'Software Tools';

      const recommendation = status === 'MET'
        ? `Skill verified and ready for technical interview rounds.`
        : getCurriculumRecommendation(skillName, studentProf, req.minProficiency);

      skillsAnalysis.push({
        skillId: req.skillId,
        skillName,
        category,
        studentProficiency: studentProf,
        requiredProficiency: req.minProficiency,
        status,
        gapLevel,
        recommendation,
      });
    });

    const totalSkills = drive.requiredSkills.length;
    let readinessPercentage = 0;
    if (totalSkills > 0) {
      // Weighted: MET = 1.0, PARTIAL = 0.5, MISSING = 0
      const totalPoints = satisfiedCount * 1.0 + partialCount * 0.5;
      readinessPercentage = Math.round((totalPoints / totalSkills) * 100);
    } else {
      readinessPercentage = 100;
    }

    // Curate tailored action plan
    const actionPlan: string[] = [];
    const missingItems = skillsAnalysis.filter((s) => s.status === 'MISSING');
    const partialItems = skillsAnalysis.filter((s) => s.status === 'PARTIAL');

    if (missingItems.length > 0) {
      actionPlan.push(`Urgent: Begin introductory modules for ${missingItems.map((m) => m.skillName).join(', ')}.`);
    }
    if (partialItems.length > 0) {
      actionPlan.push(`Bridge Gap: Schedule faculty review or practice advanced problem sets for ${partialItems.map((p) => p.skillName).join(', ')}.`);
    }
    if (skillsAnalysis.every((s) => s.status === 'MET')) {
      actionPlan.push('All required technical skills satisfied! Conduct mock technical interviews with GCET faculty panel.');
    } else {
      actionPlan.push('Leverage GCET departmental coding labs and peer review circles prior to the drive deadline.');
    }

    return {
      studentId: student.id,
      studentName: student.fullName,
      driveId: drive.id,
      driveTitle: drive.jobRole,
      companyName: drive.companyName,
      readinessPercentage,
      totalRequiredSkills: totalSkills,
      skillsSatisfiedCount: satisfiedCount,
      skillsPartialCount: partialCount,
      skillsMissingCount: missingCount,
      skills: skillsAnalysis,
      actionPlan,
    };
  },
};
