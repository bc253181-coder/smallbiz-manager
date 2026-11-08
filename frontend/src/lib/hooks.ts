import { useEffect, useState } from 'react'
import { useAuth } from '@/features/auth/AuthProvider'

export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])
  return debounced
}

/** 当前用户能否管理（新增/修改/删除）商品等资料：店主或管理员。 */
export function useCanManage(): boolean {
  const { session } = useAuth()
  const role = session?.currentTenant?.role
  return role === 'OWNER' || role === 'ADMIN'
}
