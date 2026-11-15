import React from 'react';
import { LucideIcon } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: LucideIcon;
  trend?: {
    text: string;
    positive?: boolean;
  };
  accentColor?: 'cyan' | 'blue' | 'emerald' | 'amber' | 'rose' | 'slate';
}

const ACCENT_STYLES = {
  blue: {
    iconBg: 'bg-[#2684FF]/15 text-[#2684FF] border border-[#2684FF]/30',
    topBorder: 'border-t-2 border-t-[#2684FF]',
  },
  cyan: {
    iconBg: 'bg-[#32C5F4]/15 text-[#32C5F4] border border-[#32C5F4]/30',
    topBorder: 'border-t-2 border-t-[#32C5F4]',
  },
  emerald: {
    iconBg: 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30',
    topBorder: 'border-t-2 border-t-emerald-500',
  },
  amber: {
    iconBg: 'bg-amber-500/15 text-amber-500 border border-amber-500/30',
    topBorder: 'border-t-2 border-t-amber-500',
  },
  rose: {
    iconBg: 'bg-rose-500/15 text-rose-500 border border-rose-500/30',
    topBorder: 'border-t-2 border-t-rose-500',
  },
  slate: {
    iconBg: 'bg-slate-200/50 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-300 dark:border-slate-700',
    topBorder: 'border-t-2 border-t-slate-400',
  },
};

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  icon: Icon,
  trend,
  accentColor = 'blue',
}) => {
  const styles = ACCENT_STYLES[accentColor] || ACCENT_STYLES.blue;

  return (
    <div className={`bg-white dark:bg-[#101A35] rounded-xl border border-[#E3EAF3] dark:border-[#172B50] ${styles.topBorder} p-5 shadow-xs hover:shadow-md hover:-translate-y-0.5 transition-all duration-200`}>
      <div className="flex items-center justify-between">
        <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
          {title}
        </span>
        <div className={`p-2.5 rounded-lg ${styles.iconBg} transition-transform group-hover:scale-105`}>
          <Icon className="w-4 h-4" />
        </div>
      </div>

      <div className="mt-3 flex items-baseline space-x-2">
        <div className="text-2xl font-extrabold tracking-tight text-[#172033] dark:text-white">
          {value}
        </div>
        {trend && (
          <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${
            trend.positive 
              ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800' 
              : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400'
          }`}>
            {trend.text}
          </span>
        )}
      </div>

      {subtitle && (
        <div className="mt-1.5 text-xs text-slate-500 dark:text-slate-400 leading-tight">
          {subtitle}
        </div>
      )}
    </div>
  );
};
