/** 应用入口（在 main.tsx 中、语言词典加载完成之后才被导入）。 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { PwaLayer } from '@/components/PwaLayer'
import { UpgradeDialog } from '@/features/billing/UpgradeDialog'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { router } from '@/router'

const queryClient = new QueryClient({
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
        <PwaLayer />
        <UpgradeDialog onUpgrade={() => void router.navigate('/settings/billing')} />
      </AuthProvider>
      <Toaster position="top-center" richColors closeButton />
    </QueryClientProvider>
  </StrictMode>,
  )
}
