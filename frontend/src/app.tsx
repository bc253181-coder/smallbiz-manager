/** 应用入口（在 main.tsx 中、语言词典加载完成之后才被导入）。 */
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import { toast, Toaster } from 'sonner'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { errorCode } from '@/lib/api'
import { router } from '@/router'

import { tr } from '@/lib/i18n'
const queryClient = new QueryClient({
  // 任何操作因套餐限制被拒绝时，额外提示升级入口（各页面自己的错误提示照常显示）
  mutationCache: new MutationCache({
    onError: (error) => {
      const code = errorCode(error)
      if (code === 'PLAN_FEATURE_REQUIRED' || code === 'PLAN_LIMIT_REACHED') {
        const pro = (error as { response?: { data?: { details?: { requiredPlan?: string } } } }).response?.data?.details?.requiredPlan === 'PRO'
        toast.info(pro ? tr('升级专业版即可使用') : tr('升级标准版即可使用，首月体验 ¥1.99'), {
          id: 'plan-upgrade',
          action: { label: tr('查看套餐'), onClick: () => void router.navigate('/settings/billing') },
        })
      }
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // 4xx（不存在、无权限、参数错误）重试没有意义；只对网络错误和 5xx 重试一次
      retry: (failureCount, error) => {
        const status = isAxiosError(error) ? error.response?.status : undefined
        if (status !== undefined && status >= 400 && status < 500) return false
        return failureCount < 1
      },
      refetchOnWindowFocus: false,
    },
  },
})

export function renderApp() {
  createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
      <Toaster position="top-center" richColors closeButton />
    </QueryClientProvider>
  </StrictMode>,
  )
}
