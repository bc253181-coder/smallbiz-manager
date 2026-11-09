import { LayoutDashboard, Menu, Package, Plus, ReceiptText } from 'lucide-react'
import { NavLink } from 'react-router'
import { cn } from '@/lib/utils'

import { tr } from '@/lib/i18n'
/** 手机底部导航：最常用的入口 + 中间醒目的「开单」。 */
export function MobileTabBar({ onMore }: { onMore: () => void }) {
  const item = ({ isActive }: { isActive: boolean }) =>
    cn('flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px]', isActive ? 'text-blue-600' : 'text-slate-500')

  return (
    <nav
      aria-label={tr('底部导航')}
      className="fixed inset-x-0 bottom-0 z-30 flex items-end border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden print:hidden"
    >
      <NavLink to="/" end className={item}>
        <LayoutDashboard className="size-5" aria-hidden />
        {tr('首页')}
      </NavLink>
      <NavLink to="/products" className={item}>
        <Package className="size-5" aria-hidden />
        {tr('商品')}
      </NavLink>
      <NavLink to="/sales/new" className="flex flex-1 flex-col items-center gap-0.5 pb-2 text-[11px] font-medium text-blue-600" aria-label={tr('开单')}>
        <span className="-mt-5 flex size-12 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg ring-4 ring-white">
          <Plus className="size-6" aria-hidden />
        </span>
        {tr('开单')}
      </NavLink>
      <NavLink to="/sales" end className={item}>
        <ReceiptText className="size-5" aria-hidden />
        {tr('销售')}
      </NavLink>
      <button type="button" onClick={onMore} className="flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] text-slate-500">
        <Menu className="size-5" aria-hidden />
        {tr('更多')}
      </button>
    </nav>
  )
}
