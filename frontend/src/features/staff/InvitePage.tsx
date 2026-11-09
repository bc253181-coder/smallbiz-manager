import { useQuery } from '@tanstack/react-query'
import { Store } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { AuthLayout, FullPageSpinner } from '@/components/layout/AuthLayout'
import { Button, buttonVariants } from '@/components/ui/button'
import { useAuth } from '@/features/auth/AuthProvider'
import { ROLE_LABELS } from '@/features/auth/types'
import { errorCode, errorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { ROLE_DESCRIPTIONS, staffApi } from './api'

import { tr } from '@/lib/i18n'
/** 员工打开邀请链接：未登录先登录/注册（完成后回到这里），再确认加入。 */
export function InvitePage() {
  const { token = '' } = useParams()
  const { status, session, acceptInvitation } = useAuth()
  const navigate = useNavigate()
  const [joining, setJoining] = useState(false)
  const preview = useQuery({ queryKey: ['invitation', token], queryFn: () => staffApi.preview(token), retry: false })

  if (status === 'loading' || preview.isLoading) return <FullPageSpinner />

  if (preview.isError || !preview.data) {
    return (
      <AuthLayout title={tr('邀请链接无效')} subtitle={errorMessage(preview.error, tr('邀请链接无效或已过期，请联系店主重新发送'))}>
        <Link to="/" className={buttonVariants({ variant: 'secondary', className: 'w-full' })}>
          {tr('返回首页')}
        </Link>
      </AuthLayout>
    )
  }

  const inv = preview.data
  const back = { from: `/invite/${token}` }

  async function join() {
    setJoining(true)
    try {
      await acceptInvitation(token)
      toast.success(tr('已加入「{{tenantName}}」', { tenantName: inv.tenantName }))
      navigate('/', { replace: true })
    } catch (e) {
      if (errorCode(e) === 'ALREADY_MEMBER') {
        toast.info(tr('你已经是这家店铺的成员'))
        navigate('/select-tenant', { replace: true })
      } else {
        toast.error(errorMessage(e, tr('加入失败')))
      }
    } finally {
      setJoining(false)
    }
  }

  return (
    <AuthLayout
      title={tr('加入店铺')}
      subtitle={tr('{{v0}} 邀请你以「{{v1}}」身份加入', { v0: inv.inviterName || tr('店主'), v1: ROLE_LABELS[inv.role] })}
    >
      <div className="flex items-center gap-3 rounded-lg border border-slate-200 p-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
          <Store className="size-5" aria-hidden />
        </span>
        <div className="min-w-0">
          <p className="truncate font-medium text-slate-900">{inv.tenantName}</p>
          <p className="text-xs text-slate-500">{tr('邀请')} {formatDateTime(inv.expiresAt)} {tr('前有效')}</p>
        </div>
      </div>
      <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
        {ROLE_LABELS[inv.role]}{tr('可以：')}{ROLE_DESCRIPTIONS[inv.role]}
      </p>

      {status === 'authenticated' && session ? (
        <div className="mt-6 space-y-3">
          <Button className="w-full" size="lg" loading={joining} onClick={() => void join()}>
            {tr('以')} {session.user.displayName} {tr('的身份加入')}
          </Button>
          <p className="text-center text-xs text-slate-500">{tr('当前登录：')}{session.user.email}</p>
        </div>
      ) : (
        <div className="mt-6 space-y-3">
          <Link to="/login" state={back} className={buttonVariants({ size: 'lg', className: 'w-full' })}>
            {tr('登录后加入')}
          </Link>
          <Link to="/register" state={back} className={buttonVariants({ variant: 'secondary', size: 'lg', className: 'w-full' })}>
            {tr('还没有账号？注册')}
          </Link>
        </div>
      )}
    </AuthLayout>
  )
}
