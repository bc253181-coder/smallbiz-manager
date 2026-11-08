import type { ReactNode } from 'react'
import { SupportWidget } from '@/features/support/SupportWidget'
import { Brand } from './Brand'

/** 登录、注册、创建店铺等页面的居中布局。 */
export function AuthLayout({ title, subtitle, children, wide }: {
  title: string
  subtitle?: ReactNode
  children: ReactNode
  wide?: boolean
}) {
  return (
    <div className="flex min-h-dvh flex-col bg-gradient-to-b from-blue-50/60 to-slate-50 px-4 py-10">
      <div className={wide ? 'mx-auto w-full max-w-xl' : 'mx-auto w-full max-w-sm'}>
        <Brand className="mb-8 justify-center" />
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </div>
      </div>
      <SupportWidget />
    </div>
  )
}

export function FullPageSpinner() {
  return (
    <div className="flex min-h-dvh items-center justify-center" role="status" aria-label="加载中">
      <div className="size-8 animate-spin rounded-full border-2 border-slate-200 border-t-blue-600" />
    </div>
  )
}
