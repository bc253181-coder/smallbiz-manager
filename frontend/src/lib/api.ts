import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { SessionResponse } from '@/features/auth/types'

/** 后端统一错误格式。 */
export interface ApiErrorBody {
  code: string
  message: string
  details?: { fields?: Record<string, string> } & Record<string, unknown>
}

// 访问令牌只放在内存中（刷新页面后用 HttpOnly Cookie 中的刷新令牌恢复）
let accessToken: string | null = null

type SessionListener = (session: SessionResponse | null) => void
const listeners = new Set<SessionListener>()

/** 订阅会话变化（登录、刷新、切换店铺、过期）。 */
export function onSessionChange(listener: SessionListener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** 设置新的会话：保存访问令牌并通知订阅者。 */
export function applySession(session: SessionResponse | null) {
  accessToken = session?.accessToken ?? null
  listeners.forEach((l) => l(session))
}

export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  timeout: 20000,
})

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

// 同一时间只发一个刷新请求（刷新令牌是一次性的，并发刷新会导致其中一个失败）
let refreshing: Promise<SessionResponse | null> | null = null

export function refreshSession(): Promise<SessionResponse | null> {
  if (!refreshing) {
    refreshing = axios
      .post<SessionResponse>('/api/v1/auth/refresh', null, { withCredentials: true })
      .then((res) => {
        applySession(res.data)
        return res.data
      })
      .catch(() => {
        applySession(null)
        return null
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

interface RetryableConfig extends AxiosRequestConfig {
  _retried?: boolean
}

// 访问令牌过期（401）时自动刷新一次并重试原请求
api.interceptors.response.use(undefined, async (error: AxiosError<ApiErrorBody>) => {
  const original = error.config as RetryableConfig | undefined
  const isAuthCall = original?.url?.startsWith('/auth/') && !original.url.startsWith('/auth/me')
  if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
    original._retried = true
    const session = await refreshSession()
    if (session) {
      return api(original)
    }
  }
  // 员工所在店铺的专业版到期：刷新会话（服务器不再绑定该店铺），页面随之回到选择店铺
  if (error.response?.status === 403 && error.response.data?.code === 'STORE_PLAN_EXPIRED' && original && !original._retried) {
    original._retried = true
    void refreshSession()
  }
  return Promise.reject(error)
})

/** 从错误中取出给用户看的提示文字。 */
export function errorMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorBody | undefined
    if (body?.message) return body.message
    if (!error.response) return '网络连接失败，请检查网络后重试'
  }
  return fallback
}

/** 取出后端返回的字段级错误（用于显示在对应输入框下）。 */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorBody | undefined
    return body?.details?.fields ?? {}
  }
  return {}
}

export function errorCode(error: unknown): string | undefined {
  if (error instanceof AxiosError) {
    return (error.response?.data as ApiErrorBody | undefined)?.code
  }
  return undefined
}
