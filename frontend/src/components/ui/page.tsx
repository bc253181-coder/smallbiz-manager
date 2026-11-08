import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ChevronLeft } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from './button'

export function PageHeader({
  title,
  description,
  actions,
  back,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  back?: { to: string; label: string }
}) {
  return (
    <div className="mb-6">
      {back && (
        <Link to={back.to} className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
          <ChevronLeft className="size-4" aria-hidden />
          {back.label}
        </Link>
      )}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
          {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
    </div>
  )
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: {
  icon: LucideIcon
  title: string
  description?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center gap-3 px-6 py-14 text-center', className)}>
      <span className="flex size-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Icon className="size-6" aria-hidden />
      </span>
      <h2 className="text-base font-semibold text-slate-900">{title}</h2>
      {description && <p className="max-w-sm text-sm text-slate-500">{description}</p>}
      {action}
    </div>
  )
}

export function ErrorState({ onRetry, message = '加载失败' }: { onRetry: () => void; message?: string }) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
      <p className="text-sm text-slate-600">{message}</p>
      <Button variant="secondary" size="sm" onClick={onRetry}>
        重试
      </Button>
    </div>
  )
}
