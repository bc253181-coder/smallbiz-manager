import { Lock } from 'lucide-react'
import { NavLink } from 'react-router'
import { usePlan } from '@/features/billing/api'
import { useCanManage } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { NAV_ITEMS } from './nav'

import { tr } from '@/lib/i18n'
export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const canManage = useCanManage()
  const plan = usePlan()
  return (
    <nav aria-label={tr('主导航')} className="space-y-0.5">
      {NAV_ITEMS.filter((item) => !item.managerOnly || canManage).map((item) => {
        const Icon = item.icon
        if (item.comingSoon) {
          return (
            <div
              key={item.to}
              className="flex cursor-not-allowed items-center gap-3 rounded-lg px-3 py-2 text-sm text-slate-400"
              aria-disabled="true"
              title={tr('即将上线')}
            >
              <Icon className="size-4.5 shrink-0" aria-hidden />
              <span className="flex-1">{item.label}</span>
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500">{tr('即将上线')}</span>
            </div>
          )
        }
        return (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                isActive ? 'bg-primary-soft text-blue-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
              )
            }
          >
            <Icon className="size-4.5 shrink-0" aria-hidden />
            <span className="flex-1">{item.label}</span>
            {item.feature && plan.data && !plan.data.features[item.feature] && (
              <Lock className="size-3.5 text-slate-400" aria-label={tr('高级会员功能')} />
            )}
          </NavLink>
        )
      })}
    </nav>
  )
}
