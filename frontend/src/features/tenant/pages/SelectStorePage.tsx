import { ChevronRight, Plus, Store } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/AuthProvider'
import { ROLE_LABELS } from '@/features/auth/types'
import { errorMessage } from '@/lib/api'

export function SelectStorePage() {
  const { session, switchTenant, logout } = useAuth()
  const navigate = useNavigate()
  const [pending, setPending] = useState<number | null>(null)

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

  return (
    <AuthLayout title="选择店铺" subtitle="你加入了多个店铺，请选择要进入的店铺">
      <ul className="space-y-2">
        {session?.tenants.map((t) => (
          <li key={t.id}>
            <button
              type="button"
              disabled={pending !== null}
              onClick={() => void choose(t.id)}
              className="flex w-full items-center gap-3 rounded-lg border border-slate-200 p-3 text-left hover:border-blue-300 hover:bg-blue-50/40 disabled:opacity-60"
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
          </li>
        ))}
      </ul>
      <div className="mt-6 flex items-center justify-between">
        <Link to="/stores/new" className="inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline">
          <Plus className="size-4" aria-hidden />
          新建店铺
        </Link>
        <Button
          variant="ghost"
          size="sm"
          onClick={async () => {
            await logout()
            navigate('/login', { replace: true })
          }}
        >
          退出登录
        </Button>
      </div>
    </AuthLayout>
  )
}
