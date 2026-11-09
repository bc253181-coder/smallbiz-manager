import { Check, CreditCard, Languages, LogOut, Settings, ShieldCheck } from 'lucide-react'
import { authApi } from '@/features/auth/api'
import { DropdownMenu } from 'radix-ui'
import { useNavigate } from 'react-router'
import { useAuth } from '@/features/auth/AuthProvider'

import { getLang, LANGS, setLang, tr } from '@/lib/i18n'
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
        aria-label={tr('用户菜单')}
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
            {tr('账号与店铺设置')}
          </DropdownMenu.Item>
          {session.currentTenant && (
            <DropdownMenu.Item className={itemClass} onSelect={() => navigate('/settings/billing')}>
              <CreditCard className="size-4" aria-hidden />
              {tr('套餐与续费')}
            </DropdownMenu.Item>
          )}
          {session.user.platformAdmin && (
            <DropdownMenu.Item className={itemClass} onSelect={() => navigate('/admin')}>
              <ShieldCheck className="size-4" aria-hidden />
              {tr('平台后台')}
            </DropdownMenu.Item>
          )}
          <DropdownMenu.Separator className="my-1 h-px bg-slate-100" />
          <p className="px-2 pb-1 pt-1.5 text-xs text-slate-400">{tr('界面语言')}</p>
          {LANGS.map((l) => (
            <DropdownMenu.Item
              key={l.code}
              className={itemClass}
              onSelect={async () => {
                if (l.code === getLang()) return
                try {
                  await authApi.updateLocale(l.code)
                } catch {
                  // 保存失败也照样切换本机语言
                }
                setLang(l.code)
              }}
            >
              <Languages className="size-4" aria-hidden />
              <span className="flex-1">{l.label}</span>
              {l.code === getLang() && <Check className="size-4 text-blue-600" aria-hidden />}
            </DropdownMenu.Item>
          ))}
          <DropdownMenu.Separator className="my-1 h-px bg-slate-100" />
          <DropdownMenu.Item
            className={itemClass}
            onSelect={async () => {
              await logout()
              navigate('/login', { replace: true })
            }}
          >
            <LogOut className="size-4" aria-hidden />
            {tr('退出登录')}
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
