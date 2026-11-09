import { Check, ChevronsUpDown, Plus } from 'lucide-react'
import { DropdownMenu } from 'radix-ui'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useAuth } from '@/features/auth/AuthProvider'
import { ROLE_LABELS } from '@/features/auth/types'
import { errorMessage } from '@/lib/api'

import { tr } from '@/lib/i18n'
const itemClass =
  'flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm text-slate-700 outline-none data-[highlighted]:bg-slate-100'

export function StoreSwitcher() {
  const { session, switchTenant } = useAuth()
  const navigate = useNavigate()
  const current = session?.currentTenant
  if (!session || !current) return null

  async function choose(id: number) {
    if (id === current?.id) return
    try {
      const s = await switchTenant(id)
      toast.success(tr('已切换到「{{name}}」', { name: s.currentTenant?.name }))
      navigate('/')
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className="flex max-w-56 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-left text-sm shadow-sm hover:bg-slate-50">
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium text-slate-900">{current.name}</span>
          <span className="block text-xs text-slate-500">{ROLE_LABELS[current.role]}</span>
        </span>
        <ChevronsUpDown className="size-4 shrink-0 text-slate-400" aria-hidden />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="start"
          sideOffset={6}
          className="z-50 w-64 rounded-lg border border-slate-200 bg-white p-1 shadow-lg"
        >
          <DropdownMenu.Label className="px-2 py-1.5 text-xs font-medium text-slate-500">{tr('我的店铺')}</DropdownMenu.Label>
          {session.tenants.map((t) => (
            <DropdownMenu.Item key={t.id} className={itemClass} onSelect={() => void choose(t.id)}>
              <span className="min-w-0 flex-1 truncate">{t.name}</span>
              {t.id === current.id && <Check className="size-4 text-blue-600" aria-label={tr('当前店铺')} />}
            </DropdownMenu.Item>
          ))}
          <DropdownMenu.Separator className="my-1 h-px bg-slate-100" />
          <DropdownMenu.Item className={itemClass} onSelect={() => navigate('/stores/new')}>
            <Plus className="size-4" aria-hidden />
            {tr('新建店铺')}
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
