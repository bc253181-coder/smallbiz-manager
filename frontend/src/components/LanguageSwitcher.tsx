import { Languages } from 'lucide-react'
import { authApi } from '@/features/auth/api'
import { useAuth } from '@/features/auth/AuthProvider'
import { getLang, LANGS, setLang, tr, type Lang } from '@/lib/i18n'
import { cn } from '@/lib/utils'

/** 切换界面语言：登录后保存到账号（换设备也一样），然后刷新页面。 */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { session } = useAuth()
  async function change(next: Lang) {
    if (session) {
      try {
        await authApi.updateLocale(next)
      } catch {
        // 保存失败也照样切换本机语言
      }
    }
    setLang(next)
  }
  return (
    <label className={cn('inline-flex items-center gap-1.5 text-xs text-slate-500', className)}>
      <Languages className="size-3.5" aria-hidden />
      <span className="sr-only">{tr('界面语言')}</span>
      <select
        value={getLang()}
        onChange={(e) => void change(e.target.value as Lang)}
        className="cursor-pointer bg-transparent py-0.5 text-xs text-slate-600 hover:text-slate-900 focus:outline-none"
        aria-label={tr('界面语言')}
      >
        {LANGS.map((l) => (
          <option key={l.code} value={l.code}>
            {l.label}
          </option>
        ))}
      </select>
    </label>
  )
}
