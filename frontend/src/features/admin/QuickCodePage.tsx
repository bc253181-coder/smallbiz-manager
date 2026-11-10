import axios from 'axios'
import { Copy, KeyRound } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { Field, Input } from '@/components/ui/form'
import { errorMessage } from '@/lib/api'

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制，可以发给买家了')
  } catch {
    toast.error('复制失败，请手动选择复制')
  }
}

/**
 * 快速发码（docs/18）：不需要登录和订单号，输入密码即取出一个高级永久会员激活码。
 * 用不带拦截器的 axios：密码错误返回 401 时不能触发「刷新登录后重试」（会多算一次输错）。
 */
export function QuickCodePage() {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [codes, setCodes] = useState<string[]>([])

  async function issue() {
    if (!password) {
      setError('请输入密码')
      return
    }
    setLoading(true)
    setError(null)
    try {
      const res = await axios.post<{ code: string }>('/api/v1/quick-code', { password }, { timeout: 20000 })
      setCodes((c) => [res.data.code, ...c])
    } catch (e) {
      setError(errorMessage(e, '发码失败，请稍后重试'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="快速发码" subtitle="输入密码，点一次生成一个高级永久会员激活码。每个码只能兑换一次，买家在「会员」页输入即可开通">
      <form
        className="space-y-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          void issue()
        }}
      >
        <Field label="密码" htmlFor="qc-password" error={error ?? undefined}>
          <Input
            id="qc-password"
            type="password"
            autoComplete="current-password"
            value={password}
            invalid={!!error}
            onChange={(e) => {
              setPassword(e.target.value)
              setError(null)
            }}
          />
        </Field>
        <Button type="submit" className="w-full" loading={loading}>
          <KeyRound aria-hidden />
          生成激活码
        </Button>
      </form>

      {codes.length > 0 && (
        <ul className="mt-6 space-y-2" aria-label="生成的激活码">
          {codes.map((code, i) => (
            <li key={code} className={i === 0 ? 'flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2' : 'flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2'}>
              <span
                className={i === 0 ? 'min-w-0 flex-1 break-all font-mono text-lg font-semibold tracking-wider text-emerald-900' : 'min-w-0 flex-1 break-all font-mono text-sm text-slate-600'}
                data-testid={i === 0 ? 'quick-code' : undefined}
              >
                {code}
              </span>
              <Button size="sm" variant="secondary" onClick={() => void copy(code)}>
                <Copy aria-hidden />
                复制
              </Button>
            </li>
          ))}
        </ul>
      )}
      {codes.length > 1 && <p className="mt-2 text-xs text-slate-500">本页生成了 {codes.length} 个，刷新页面后列表会清空（发码台里可以按「快速发码」搜索到）</p>}
    </AuthLayout>
  )
}
