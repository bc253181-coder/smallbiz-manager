import { NavLink } from 'react-router'
import { cn } from '@/lib/utils'
import { NAV_ITEMS } from './nav'

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="主导航" className="space-y-0.5">
      {NAV_ITEMS.map((item) => {
        const Icon = item.icon
        if (item.comingSoon) {
          return (
            <div
              key={item.to}
              className="flex cursor-not-allowed items-center gap-3 rounded-lg px-3 py-2 text-sm text-slate-400"
              aria-disabled="true"
              title="即将上线"
            >
              <Icon className="size-4.5 shrink-0" aria-hidden />
              <span className="flex-1">{item.label}</span>
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500">即将上线</span>
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
            {item.label}
          </NavLink>
        )
      })}
    </nav>
  )
}
