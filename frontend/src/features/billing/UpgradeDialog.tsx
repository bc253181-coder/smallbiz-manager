import { AlertDialog } from 'radix-ui'
import { Crown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatMoney } from '@/lib/format'
import { tr } from '@/lib/i18n'
import { closeUpgrade, useUpgradeReason, type UpgradeReason } from '@/lib/upgrade'
import { FEATURE_LABELS, useLifetimePrice, type Feature } from './api'

function texts(r: UpgradeReason): { title: string; body: string } {
  const tail = tr('升级高级永久会员，即可解锁完整功能，不再受到普通会员次数限制。')
  switch (r.kind) {
    case 'stores':
      return { title: tr('已达到免费使用次数'), body: tr('你已经使用完当前普通会员的店铺创建额度。') + tail }
    case 'products':
      return { title: tr('已达到免费使用次数'), body: tr('你已经使用完当前普通会员的商品创建额度。') + tail }
    case 'reportDays':
      return {
        title: tr('升级查看全部历史'),
        body: tr('普通会员的报表只能查看最近 {{days}} 天。', { days: r.days ?? '' }) + tail,
      }
    case 'feature': {
      const name = r.feature && r.feature in FEATURE_LABELS ? FEATURE_LABELS[r.feature as Feature] : tr('这项功能')
      return { title: tr('高级会员功能'), body: tr('「{{name}}」是高级会员功能。', { name }) + tail }
    }
  }
}

/**
 * 升级窗口（全局一个）。文字按触发原因显示；价格取当前币种的永久会员价格。
 * onUpgrade：跳转到会员页（由使用方传入，避免依赖路由上下文）。
 */
export function UpgradeDialog({ onUpgrade }: { onUpgrade: () => void }) {
  const reason = useUpgradeReason()
  const price = useLifetimePrice()
  if (!reason) return null
  const t = texts(reason)
  return (
    <AlertDialog.Root open onOpenChange={(o) => !o && closeUpgrade()}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 z-50 bg-slate-900/40" />
        <AlertDialog.Content className="fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
          <div className="flex size-11 items-center justify-center rounded-full bg-amber-50 text-amber-600">
            <Crown className="size-6" aria-hidden />
          </div>
          <AlertDialog.Title className="mt-3 text-lg font-semibold text-slate-900">{t.title}</AlertDialog.Title>
          <AlertDialog.Description className="mt-2 text-sm text-slate-600">{t.body}</AlertDialog.Description>
          {price && (
            <p className="mt-4 text-3xl font-semibold tabular-nums text-slate-900" data-testid="upgrade-price">
              {formatMoney(price.amount, price.currency)}
            </p>
          )}
          <p className="mt-1 text-xs text-slate-500">{tr('一次性付款，永久享有高级会员权益，无需自动续费。')}</p>
          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <AlertDialog.Cancel asChild>
              <Button variant="ghost">{tr('以后再说')}</Button>
            </AlertDialog.Cancel>
            <Button
              onClick={() => {
                closeUpgrade()
                onUpgrade()
              }}
            >
              {tr('立即升级')}
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
