import { ArrowLeft, ShieldCheck } from 'lucide-react'
import { Suspense } from 'react'
import { Link, Navigate, NavLink, Outlet } from 'react-router'
import { PageLoading } from '@/components/layout/PageLoading'
import { useAuth } from '@/features/auth/AuthProvider'
import { cn } from '@/lib/utils'

const TABS = [
  { to: '/admin', label: '概况', end: true },
  { to: '/admin/payments', label: '付款审核' },
  { to: '/admin/tenants', label: '店铺' },
  { to: '/admin/dispenser', label: '发码台' },
  { to: '/admin/codes', label: '激活码' },
  { to: '/admin/config', label: '价格与收款' },
  { to: '/admin/support', label: '客服话术' },
]

/** 平台后台框架：仅平台管理员；与店铺业务界面分开。 */
export function AdminLayout() {
  const { session } = useAuth()
  if (!session?.user.platformAdmin) return <Navigate to="/" replace />

  return (
    <div className="min-h-dvh bg-slate-50">
      <header className="sticky top-0 z-30 border-b border-slate-800 bg-slate-900 text-white">
        <div className="mx-auto flex h-14 max-w-7xl items-center gap-3 px-4 sm:px-6 lg:px-8">
          <ShieldCheck className="size-5 text-emerald-400" aria-hidden />
          <span className="font-semibold">SmallBiz 平台后台</span>
          <div className="flex-1" />
          <Link to="/" className="inline-flex items-center gap-1 text-sm text-slate-300 hover:text-white">
            <ArrowLeft className="size-4" aria-hidden />
            返回店铺
          </Link>
        </div>
        <nav className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-2 sm:px-4 lg:px-6" aria-label="后台导航">
          {TABS.map((t) => (
            <NavLink
              key={t.to}
              to={t.to}
              end={t.end}
              className={({ isActive }) =>
                cn(
                  'whitespace-nowrap border-b-2 px-3 py-2.5 text-sm',
                  isActive ? 'border-emerald-400 font-medium text-white' : 'border-transparent text-slate-400 hover:text-slate-200',
                )
              }
            >
              {t.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        <Suspense fallback={<PageLoading />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}
