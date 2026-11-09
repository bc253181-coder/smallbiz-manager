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
      <AuthLayout title="邀请链接无效" subtitle={errorMessage(preview.error, '邀请链接无效或已过期，请联系店主重新发送')}>
        <Link to="/" className={buttonVariants({ variant: 'secondary', className: 'w-full' })}>
          返回首页
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
      toast.success(`已加入「${inv.tenantName}」`)
      navigate('/', { replace: true })
    } catch (e) {
      if (errorCode(e) === 'ALREADY_MEMBER') {
        toast.info('你已经是这家店铺的成员')
        navigate('/select-tenant', { replace: true })
      } else {
        toast.error(errorMessage(e, '加入失败'))
      }
    } finally {
      setJoining(false)
    }
  }

  return (
    <AuthLayout
      title="加入店铺"
      subtitle={`${inv.inviterName || '店主'} 邀请你以「${ROLE_LABELS[inv.role]}」身份加入`}
    >
      <div className="flex items-center gap-3 rounded-lg border border-slate-200 p-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
          <Store className="size-5" aria-hidden />
        </span>
        <div className="min-w-0">
          <p className="truncate font-medium text-slate-900">{inv.tenantName}</p>
          <p className="text-xs text-slate-500">邀请 {formatDateTime(inv.expiresAt)} 前有效</p>
        </div>
      </div>
      <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
        {ROLE_LABELS[inv.role]}可以：{ROLE_DESCRIPTIONS[inv.role]}
      </p>

      {status === 'authenticated' && session ? (
        <div className="mt-6 space-y-3">
          <Button className="w-full" size="lg" loading={joining} onClick={() => void join()}>
            以 {session.user.displayName} 的身份加入
          </Button>
          <p className="text-center text-xs text-slate-500">当前登录：{session.user.email}</p>
        </div>
      ) : (
        <div className="mt-6 space-y-3">
          <Link to="/login" state={back} className={buttonVariants({ size: 'lg', className: 'w-full' })}>
            登录后加入
          </Link>
          <Link to="/register" state={back} className={buttonVariants({ variant: 'secondary', size: 'lg', className: 'w-full' })}>
            还没有账号？注册
          </Link>
        </div>
      )}
    </AuthLayout>
  )
}
