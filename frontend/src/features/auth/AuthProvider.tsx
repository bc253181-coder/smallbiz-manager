import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { applySession, onSessionChange, refreshSession } from '@/lib/api'
import { getLang, syncLangFromAccount } from '@/lib/i18n'
import { authApi, tenantApi } from './api'
import type { BusinessType, SessionResponse } from './types'

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  status: Status
  session: SessionResponse | null
  login: (email: string, password: string) => Promise<SessionResponse>
  register: (email: string, password: string, displayName: string) => Promise<SessionResponse>
  logout: () => Promise<void>
  createTenant: (name: string, businessType: BusinessType, currency?: string) => Promise<SessionResponse>
  switchTenant: (tenantId: number) => Promise<SessionResponse>
  /** 接受员工邀请：加入店铺并切换过去。 */
  acceptInvitation: (token: string) => Promise<SessionResponse>
  /** 员工退出某家店铺。 */
  leaveTenant: (tenantId: number) => Promise<SessionResponse>
  /** 更新本地会话中的用户/店铺信息（例如修改昵称或店名后）。 */
  patchSession: (updater: (s: SessionResponse) => SessionResponse) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionResponse | null>(null)
  const [status, setStatus] = useState<Status>('loading')
  const queryClient = useQueryClient()

  useEffect(() => {
    const unsubscribe = onSessionChange((s) => {
      // 界面语言跟着账号：账号上的语言与本机不同时以账号为准（刷新页面）；账号还没设置时记下当前语言
      if (s?.user) {
        if (syncLangFromAccount(s.user.locale)) {
          window.location.reload()
          return
        }
        if (!s.user.locale) void authApi.updateLocale(getLang()).catch(() => undefined)
      }
      setSession(s)
      setStatus(s ? 'authenticated' : 'anonymous')
    })
    // 打开页面时用 HttpOnly Cookie 恢复登录状态
    void refreshSession()
    return () => {
      unsubscribe()
    }
  }, [])

  const accept = useCallback(
    (s: SessionResponse) => {
      // 换了用户或店铺：清空所有缓存数据，避免显示上一个店铺的数据
      queryClient.clear()
      applySession(s)
      return s
    },
    [queryClient],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      session,
      login: async (email, password) => accept(await authApi.login({ email, password })),
      register: async (email, password, displayName) => accept(await authApi.register({ email, password, displayName, agreeTerms: true })),
      logout: async () => {
        try {
          await authApi.logout()
        } finally {
          queryClient.clear()
          applySession(null)
        }
      },
      createTenant: async (name, businessType, currency) => accept(await tenantApi.create({ name, businessType, currency })),
      switchTenant: async (tenantId) => accept(await tenantApi.switchTo(tenantId)),
      acceptInvitation: async (token) => accept(await tenantApi.acceptInvitation(token)),
      leaveTenant: async (tenantId) => accept(await tenantApi.leave(tenantId)),
      patchSession: (updater) => setSession((s) => (s ? updater(s) : s)),
    }),
    [status, session, accept, queryClient],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
