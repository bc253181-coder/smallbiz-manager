import { CreditCard, LogOut, Settings, ShieldCheck } from 'lucide-react'
import { DropdownMenu } from 'radix-ui'
import { useNavigate } from 'react-router'
import { useAuth } from '@/features/auth/AuthProvider'

const itemClass =
  'flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm text-slate-700 outline-none data-[highlighted]:bg-slate-100'

export function UserMenu() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  if (!session) return null
  const initial = session.user.displayName.trim().charAt(0).toUpperCase() || '?'

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger
        className="flex size-9 items-center justify-center rounded-full bg-blue-100 text-sm font-semibold text-blue-700 hover:ring-2 hover:ring-blue-200"
        aria-label="用户菜单"
      >
        {initial}
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          className="z-50 w-60 rounded-lg border border-slate-200 bg-white p-1 shadow-lg"
        >
          <div className="px-2 py-2">
            <p className="truncate text-sm font-medium text-slate-900">{session.user.displayName}</p>
            <p className="truncate text-xs text-slate-500">{session.user.email}</p>
          </div>
          <DropdownMenu.Separator className="my-1 h-px bg-slate-100" />
          <DropdownMenu.Item className={itemClass} onSelect={() => navigate('/settings')}>
            <Settings className="size-4" aria-hidden />
            账号与店铺设置
          </DropdownMenu.Item>
          {session.currentTenant && (
            <DropdownMenu.Item className={itemClass} onSelect={() => navigate('/settings/billing')}>
              <CreditCard className="size-4" aria-hidden />
              套餐与续费
            </DropdownMenu.Item>
          )}
          {session.user.platformAdmin && (
            <DropdownMenu.Item className={itemClass} onSelect={() => navigate('/admin')}>
              <ShieldCheck className="size-4" aria-hidden />
              平台后台
            </DropdownMenu.Item>
          )}
          <DropdownMenu.Item
            className={itemClass}
            onSelect={async () => {
              await logout()
              navigate('/login', { replace: true })
            }}
          >
            <LogOut className="size-4" aria-hidden />
            退出登录
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
