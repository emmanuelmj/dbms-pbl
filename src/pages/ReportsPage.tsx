import React, { useState, useMemo } from 'react';
import {
  BarChart3,
  Download,
  Printer,
  PieChart,
  TrendingUp,
  Award,
  Users,
  Building2,
  Layers,
  FileSpreadsheet,
} from 'lucide-react';
import { Student, Company, PlacementDrive, Application, Skill } from '../types';

interface ReportsPageProps {
  students: Student[];
  companies: Company[];
  drives: PlacementDrive[];
  applications: Application[];
  skills: Skill[];
  onShowToast: (type: 'success' | 'error' | 'info', title: string, message?: string) => void;
}

export const ReportsPage: React.FC<ReportsPageProps> = ({
  students,
  companies,
  drives,
  applications,
  skills,
  onShowToast,
}) => {
  const [activeReportTab, setActiveReportTab] = useState<'branch' | 'cgpa' | 'skills' | 'companies' | 'funnel'>('branch');

  // Branch Breakdown
  const branchStats = useMemo(() => {
    const map: Record<string, { total: number; placed: number; shortlisted: number; unplaced: number; totalCgpa: number }> = {};
    students.forEach((s) => {
      if (!map[s.branch]) {
        map[s.branch] = { total: 0, placed: 0, shortlisted: 0, unplaced: 0, totalCgpa: 0 };
      }
      map[s.branch].total += 1;
      map[s.branch].totalCgpa += s.cgpa;
      if (s.placementStatus === 'PLACED') map[s.branch].placed += 1;
      else if (s.placementStatus === 'SHORTLISTED') map[s.branch].shortlisted += 1;
      else map[s.branch].unplaced += 1;
    });

    return Object.entries(map).map(([branch, stats]) => ({
      branch,
      total: stats.total,
      placed: stats.placed,
      shortlisted: stats.shortlisted,
      unplaced: stats.unplaced,
      placementRate: stats.total > 0 ? Math.round((stats.placed / stats.total) * 100) : 0,
      avgCgpa: stats.total > 0 ? (stats.totalCgpa / stats.total).toFixed(2) : '0.00',
    }));
  }, [students]);

  // CGPA Distribution
  const cgpaDistribution = useMemo(() => {
    const bins = {
      '9.00 - 10.00': 0,
      '8.00 - 8.99': 0,
      '7.00 - 7.99': 0,
      '6.00 - 6.99': 0,
      'Below 6.00': 0,
    };

    students.forEach((s) => {
      if (s.cgpa >= 9.0) bins['9.00 - 10.00'] += 1;
      else if (s.cgpa >= 8.0) bins['8.00 - 8.99'] += 1;
      else if (s.cgpa >= 7.0) bins['7.00 - 7.99'] += 1;
      else if (s.cgpa >= 6.0) bins['6.00 - 6.99'] += 1;
      else bins['Below 6.00'] += 1;
    });

    return Object.entries(bins).map(([range, count]) => ({
      range,
      count,
      pct: students.length > 0 ? Math.round((count / students.length) * 100) : 0,
    }));
  }, [students]);

  // Skill Demand & Student Proficiency
  const topSkillsReport = useMemo(() => {
    return skills.map((skill) => {
      // Count drives requiring this skill
      const requiredByDrives = drives.filter((d) => d.requiredSkills.some((r) => r.skillId === skill.id)).length;
      // Count students proficient (level >= 3)
      const studentsProficient = students.filter((st) => {
        const entry = st.skills.find((sk) => sk.skillId === skill.id);
        return entry && entry.proficiency >= 3;
      }).length;

      return {
        skillName: skill.name,
        category: skill.category,
        requiredByDrives,
        studentsProficient,
      };
    }).sort((a, b) => (b.requiredByDrives * 2 + b.studentsProficient) - (a.requiredByDrives * 2 + a.studentsProficient)).slice(0, 10);
  }, [skills, drives, students]);

  // Company Recruitment Stats
  const companyStats = useMemo(() => {
    return companies.map((c) => {
      const companyApps = applications.filter((a) => a.companyId === c.id);
      const hired = companyApps.filter((a) => a.currentStage === 'JOINED' || a.currentStage === 'OFFERED').length;
      const drivesCount = drives.filter((d) => d.companyId === c.id).length;

      return {
        companyName: c.name,
        tier: c.tier,
        drivesCount,
        applicationsReceived: companyApps.length,
        candidatesHired: hired,
      };
    });
  }, [companies, applications, drives]);

  // Conversion Funnel
  const funnelStages = useMemo(() => {
    const counts = {
      'Application Submitted': applications.length,
      'Shortlisted': applications.filter((a) => !['APPLICATION_SUBMITTED', 'WITHDRAWN', 'REJECTED'].includes(a.currentStage)).length,
      'Assessment / Interview': applications.filter((a) => ['ONLINE_ASSESSMENT', 'TECHNICAL_INTERVIEW', 'HR_INTERVIEW', 'SELECTED', 'OFFERED', 'JOINED'].includes(a.currentStage)).length,
      'Final Selection & Offers': applications.filter((a) => ['SELECTED', 'OFFERED', 'JOINED'].includes(a.currentStage)).length,
      'Offers Accepted / Joined': applications.filter((a) => a.currentStage === 'JOINED').length,
    };
    return Object.entries(counts).map(([label, count]) => ({
      label,
      count,
      pct: applications.length > 0 ? Math.round((count / applications.length) * 100) : 0,
    }));
  }, [applications]);

  // CSV Export for active table
  const handleExportCsv = () => {
    let headers: string[] = [];
    let rows: (string | number)[][] = [];
    let filename = `GCET_Report_${activeReportTab}_${new Date().toISOString().split('T')[0]}.csv`;

    if (activeReportTab === 'branch') {
      headers = ['Branch', 'Total Students', 'Placed', 'Shortlisted', 'Unplaced', 'Placement Rate %', 'Average CGPA'];
      rows = branchStats.map((b) => [b.branch, b.total, b.placed, b.shortlisted, b.unplaced, `${b.placementRate}%`, b.avgCgpa]);
    } else if (activeReportTab === 'cgpa') {
      headers = ['CGPA Range', 'Student Count', 'Percentage %'];
      rows = cgpaDistribution.map((c) => [c.range, c.count, `${c.pct}%`]);
    } else if (activeReportTab === 'skills') {
      headers = ['Skill Name', 'Category', 'Required By Drives', 'Proficient Students (Lvl 3+)'];
      rows = topSkillsReport.map((s) => [s.skillName, s.category, s.requiredByDrives, s.studentsProficient]);
    } else if (activeReportTab === 'companies') {
      headers = ['Company Name', 'Tier', 'Drives Conducted', 'Applications Received', 'Candidates Hired'];
      rows = companyStats.map((c) => [c.companyName, c.tier, c.drivesCount, c.applicationsReceived, c.candidatesHired]);
    } else if (activeReportTab === 'funnel') {
      headers = ['Recruitment Funnel Stage', 'Count', 'Conversion %'];
      rows = funnelStages.map((f) => [f.label, f.count, `${f.pct}%`]);
    }

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    onShowToast('info', 'Report Downloaded', `Exported ${filename}`);
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="space-y-6">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-900 dark:text-slate-100">Institutional Reports &amp; Placement Analytics</h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Geethanjali College of Engineering and Technology · Real queries over MySQL placement records.
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={handlePrint}
            className="px-3.5 py-2 rounded-lg border border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-200 text-xs font-medium transition cursor-pointer flex items-center space-x-1.5"
          >
            <Printer className="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" />
            <span>Print Report</span>
          </button>

          <button
            onClick={handleExportCsv}
            className="px-3.5 py-2 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition cursor-pointer flex items-center space-x-1.5 shadow-xs"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {/* Report Category Navigation Bar */}
      <div className="bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] p-1.5 shadow-xs flex flex-wrap gap-1">
        <button
          onClick={() => setActiveReportTab('branch')}
          className={`px-3 py-2 rounded-lg text-xs font-semibold transition cursor-pointer flex items-center space-x-2 ${
            activeReportTab === 'branch'
              ? 'bg-[#2684FF] text-white shadow-xs'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
          }`}
        >
          <Users className="w-3.5 h-3.5" />
          <span>Branch-Wise Distribution</span>
        </button>

        <button
          onClick={() => setActiveReportTab('cgpa')}
          className={`px-3 py-2 rounded-lg text-xs font-semibold transition cursor-pointer flex items-center space-x-2 ${
            activeReportTab === 'cgpa'
              ? 'bg-[#2684FF] text-white shadow-xs'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
          }`}
        >
          <BarChart3 className="w-3.5 h-3.5" />
          <span>CGPA Spread</span>
        </button>

        <button
          onClick={() => setActiveReportTab('skills')}
          className={`px-3 py-2 rounded-lg text-xs font-semibold transition cursor-pointer flex items-center space-x-2 ${
            activeReportTab === 'skills'
              ? 'bg-[#2684FF] text-white shadow-xs'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
          }`}
        >
          <Layers className="w-3.5 h-3.5" />
          <span>Skill Demand Matrix</span>
        </button>

        <button
          onClick={() => setActiveReportTab('companies')}
          className={`px-3 py-2 rounded-lg text-xs font-semibold transition cursor-pointer flex items-center space-x-2 ${
            activeReportTab === 'companies'
              ? 'bg-[#2684FF] text-white shadow-xs'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
          }`}
        >
          <Building2 className="w-3.5 h-3.5" />
          <span>Company Hires</span>
        </button>

        <button
          onClick={() => setActiveReportTab('funnel')}
          className={`px-3 py-2 rounded-lg text-xs font-semibold transition cursor-pointer flex items-center space-x-2 ${
            activeReportTab === 'funnel'
              ? 'bg-[#2684FF] text-white shadow-xs'
              : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-[#172B50]'
          }`}
        >
          <TrendingUp className="w-3.5 h-3.5" />
          <span>Recruitment Funnel</span>
        </button>
      </div>

      {/* Tab 1: Branch-wise Report */}
      {activeReportTab === 'branch' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
          <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs">
            <span className="font-bold text-slate-800 dark:text-slate-200">Branch-wise Placement Statistics &amp; Academic Averages</span>
            <span className="text-slate-500 dark:text-slate-400">Total Branches: {branchStats.length}</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 dark:bg-slate-950/80 text-slate-600 dark:text-slate-300 font-semibold border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th className="py-3 px-4">Engineering Branch</th>
                  <th className="py-3 px-4">Enrolled Students</th>
                  <th className="py-3 px-4">Placed</th>
                  <th className="py-3 px-4">Shortlisted</th>
                  <th className="py-3 px-4">Unplaced</th>
                  <th className="py-3 px-4">Placement Rate</th>
                  <th className="py-3 px-4">Average CGPA</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {branchStats.map((b) => (
                  <tr key={b.branch} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/50 transition">
                    <td className="py-3 px-4 font-bold text-slate-900 dark:text-slate-100">{b.branch}</td>
                    <td className="py-3 px-4 text-slate-800 dark:text-slate-200">{b.total}</td>
                    <td className="py-3 px-4 font-semibold text-emerald-700 dark:text-emerald-400">{b.placed}</td>
                    <td className="py-3 px-4 font-medium text-amber-700 dark:text-amber-400">{b.shortlisted}</td>
                    <td className="py-3 px-4 text-slate-600 dark:text-slate-400">{b.unplaced}</td>
                    <td className="py-3 px-4">
                      <div className="flex items-center space-x-2">
                        <div className="w-20 bg-slate-100 dark:bg-slate-800 h-2 rounded-full overflow-hidden">
                          <div
                            className="bg-cyan-600 h-full rounded-full"
                            style={{ width: `${b.placementRate}%` }}
                          />
                        </div>
                        <span className="font-semibold text-slate-800 dark:text-slate-200 text-[11px]">{b.placementRate}%</span>
                      </div>
                    </td>
                    <td className="py-3 px-4 font-mono font-medium text-slate-800 dark:text-slate-200">{b.avgCgpa}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 2: CGPA Distribution */}
      {activeReportTab === 'cgpa' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs space-y-4">
          <div className="pb-3 border-b border-slate-100 dark:border-slate-800">
            <h3 className="text-xs font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wider">
              CGPA Tier Distribution Across Active Cohort
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Categorized based on cumulative marks submitted to JNTUH.</p>
          </div>

          <div className="space-y-3">
            {cgpaDistribution.map((item) => (
              <div key={item.range} className="text-xs space-y-1">
                <div className="flex justify-between items-center text-slate-800 dark:text-slate-200">
                  <span className="font-semibold">{item.range}</span>
                  <span className="text-slate-500 dark:text-slate-400 font-mono">
                    {item.count} Students ({item.pct}%)
                  </span>
                </div>
                <div className="w-full bg-slate-100 dark:bg-slate-800 h-3 rounded-full overflow-hidden flex">
                  <div
                    className="bg-cyan-600 h-full rounded-full transition-all duration-300"
                    style={{ width: `${item.pct}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab 3: Skills Report */}
      {activeReportTab === 'skills' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
          <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs">
            <span className="font-bold text-slate-800 dark:text-slate-200">Highest In-Demand Skills vs Student Competency</span>
            <span className="text-slate-500 dark:text-slate-400">Benchmark: Proficiency Level 3+</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 dark:bg-slate-950/80 text-slate-600 dark:text-slate-300 font-semibold border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th className="py-3 px-4">Technical Skill</th>
                  <th className="py-3 px-4">Domain Category</th>
                  <th className="py-3 px-4">Required by Drives</th>
                  <th className="py-3 px-4">Proficient Students (Level 3+)</th>
                  <th className="py-3 px-4">Talent Availability Ratio</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {topSkillsReport.map((s) => {
                  const ratio = s.requiredByDrives > 0 ? (s.studentsProficient / (s.requiredByDrives * 5)).toFixed(1) : 'High';
                  return (
                    <tr key={s.skillName} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/50 transition">
                      <td className="py-3 px-4 font-bold text-slate-900 dark:text-slate-100">{s.skillName}</td>
                      <td className="py-3 px-4 text-cyan-700 dark:text-cyan-400 font-medium">{s.category}</td>
                      <td className="py-3 px-4 font-semibold text-slate-800 dark:text-slate-200">{s.requiredByDrives} Drives</td>
                      <td className="py-3 px-4 font-semibold text-emerald-700 dark:text-emerald-400">{s.studentsProficient} Candidates</td>
                      <td className="py-3 px-4 text-slate-600 dark:text-slate-400 font-mono text-[11px]">{ratio}x supply ratio</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 4: Company Hires */}
      {activeReportTab === 'companies' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-xs">
          <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs">
            <span className="font-bold text-slate-800 dark:text-slate-200">Corporate Hiring Summary &amp; Application Conversions</span>
            <span className="text-slate-500 dark:text-slate-400">Partners: {companyStats.length}</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 dark:bg-slate-950/80 text-slate-600 dark:text-slate-300 font-semibold border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th className="py-3 px-4">Partner Company</th>
                  <th className="py-3 px-4">Tier</th>
                  <th className="py-3 px-4">Total Drives</th>
                  <th className="py-3 px-4">Applications Received</th>
                  <th className="py-3 px-4">Offers Extended / Placed</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {companyStats.map((c) => (
                  <tr key={c.companyName} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/50 transition">
                    <td className="py-3 px-4 font-bold text-slate-900 dark:text-slate-100">{c.companyName}</td>
                    <td className="py-3 px-4 text-slate-600 dark:text-slate-400">{c.tier}</td>
                    <td className="py-3 px-4 text-slate-800 dark:text-slate-200 font-medium">{c.drivesCount}</td>
                    <td className="py-3 px-4 text-slate-800 dark:text-slate-200">{c.applicationsReceived}</td>
                    <td className="py-3 px-4 font-bold text-emerald-700 dark:text-emerald-400">{c.candidatesHired}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 5: Funnel */}
      {activeReportTab === 'funnel' && (
        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 shadow-xs space-y-4">
          <div className="pb-3 border-b border-slate-100 dark:border-slate-800">
            <h3 className="text-xs font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wider">
              Recruitment Conversion Funnel
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Tracking candidates moving through selection checkpoints.</p>
          </div>

          <div className="space-y-4 max-w-2xl">
            {funnelStages.map((st, i) => (
              <div key={st.label} className="text-xs space-y-1">
                <div className="flex justify-between items-center text-slate-800 dark:text-slate-200">
                  <span className="font-semibold">{i + 1}. {st.label}</span>
                  <span className="font-mono text-cyan-800 dark:text-cyan-400 font-bold">
                    {st.count} Candidates ({st.pct}%)
                  </span>
                </div>
                <div className="w-full bg-slate-100 dark:bg-slate-800 h-3 rounded-full overflow-hidden">
                  <div
                    className="bg-gradient-to-r from-slate-800 dark:from-slate-700 to-cyan-600 h-full rounded-full transition-all duration-300"
                    style={{ width: `${Math.max(5, st.pct)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

    </div>
  );
};
