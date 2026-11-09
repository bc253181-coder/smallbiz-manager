import { ChevronRight, Lock, Plus, Store } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { useAuth } from '@/features/auth/AuthProvider'
import { ROLE_LABELS, type TenantSummary } from '@/features/auth/types'
import { errorMessage } from '@/lib/api'

import { tr } from '@/lib/i18n'
export function SelectStorePage() {
  const { session, switchTenant, leaveTenant, logout } = useAuth()
  const navigate = useNavigate()
  const [pending, setPending] = useState<number | null>(null)
  const [leaving, setLeaving] = useState<TenantSummary | null>(null)
  const [leavePending, setLeavePending] = useState(false)

  async function choose(id: number) {
    setPending(id)
    try {
      await switchTenant(id)
      navigate('/', { replace: true })
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setPending(null)
    }
  }

  async function leave(t: TenantSummary) {
    setLeavePending(true)
    try {
      const s = await leaveTenant(t.id)
      toast.success(tr('已退出「{{name}}」', { name: t.name }))
      setLeaving(null)
      if (s.tenants.length === 0) navigate('/onboarding', { replace: true })
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setLeavePending(false)
    }
  }

  return (
    <AuthLayout title={tr('选择店铺')} subtitle={tr('请选择要进入的店铺')}>
      <ul className="space-y-2">
        {session?.tenants.map((t) => (
          <li key={t.id} className="rounded-lg border border-slate-200">
            {t.accessible ? (
              <button
                type="button"
                disabled={pending !== null}
                onClick={() => void choose(t.id)}
                className="flex w-full items-center gap-3 rounded-lg p-3 text-left hover:bg-blue-50/40 disabled:opacity-60"
              >
                <span className="flex size-9 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
                  <Store className="size-4.5" aria-hidden />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-slate-900">{t.name}</span>
                  <span className="block text-xs text-slate-500">{ROLE_LABELS[t.role]}</span>
                </span>
                {pending === t.id ? (
                  <span className="size-4 animate-spin rounded-full border-2 border-slate-200 border-t-blue-600" />
                ) : (
                  <ChevronRight className="size-4 text-slate-400" aria-hidden />
                )}
              </button>
            ) : (
              <div className="flex items-center gap-3 p-3">
                <span className="flex size-9 items-center justify-center rounded-lg bg-slate-100 text-slate-400">
                  <Lock className="size-4.5" aria-hidden />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-slate-500">{t.name}</span>
                  <span className="block text-xs text-amber-700">{tr('店铺套餐已到期，暂时不能进入，请联系店主续费')}</span>
                </span>
              </div>
            )}
            {t.role !== 'OWNER' && (
              <div className="border-t border-slate-100 px-3 py-1.5 text-right">
                <button type="button" className="text-xs text-slate-500 hover:text-red-600" onClick={() => setLeaving(t)}>
                  {tr('退出这家店铺')}
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
      <div className="mt-6 flex items-center justify-between">
        <Link to="/stores/new" className="inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline">
          <Plus className="size-4" aria-hidden />
          {tr('新建店铺')}
        </Link>
        <Button
          variant="ghost"
          size="sm"
          onClick={async () => {
            await logout()
            navigate('/login', { replace: true })
          }}
        >
          {tr('退出登录')}
        </Button>
      </div>
      <ConfirmDialog
        open={!!leaving}
        onOpenChange={(o) => !o && setLeaving(null)}
        title={tr('退出「{{v0}}」？', { v0: leaving?.name ?? '' })}
        description={tr('退出后不能再进入这家店铺，需要店主重新邀请。你开过的单会保留。')}
        confirmText={tr('退出店铺')}
        danger
        loading={leavePending}
        onConfirm={() => leaving && void leave(leaving)}
      />
    </AuthLayout>
  )
}
