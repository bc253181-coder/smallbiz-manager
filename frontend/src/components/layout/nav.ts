import {
  ChartColumn,
  Contact,
  Factory,
  HandCoins,
  LayoutDashboard,
  Package,
  ReceiptText,
  Settings,
  Truck,
  Warehouse,
  type LucideIcon,
} from 'lucide-react'
import type { Feature } from '@/features/billing/api'

import { tr } from '@/lib/i18n'
export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  /** 尚未上线的模块：显示但不可点击，并标注上线阶段。 */
  comingSoon?: boolean
  /** 仅店主/管理员可见。 */
  managerOnly?: boolean
  /** 需要付费套餐的功能：未开通时显示锁图标（仍可进入查看升级说明）。 */
  feature?: Feature
}

export const NAV_ITEMS: NavItem[] = [
  { label: tr('仪表盘'), to: '/', icon: LayoutDashboard },
  { label: tr('商品'), to: '/products', icon: Package },
  { label: tr('库存'), to: '/inventory', icon: Warehouse },
  { label: tr('销售'), to: '/sales', icon: ReceiptText },
  { label: tr('采购'), to: '/purchases', icon: Truck, managerOnly: true, feature: 'PURCHASING' },
  { label: tr('客户'), to: '/customers', icon: Contact, feature: 'CUSTOMERS' },
  { label: tr('应收账款'), to: '/receivables', icon: HandCoins, managerOnly: true, feature: 'RECEIVABLES' },
  { label: tr('供应商'), to: '/suppliers', icon: Factory, managerOnly: true, feature: 'SUPPLIERS' },
  { label: tr('报表'), to: '/reports', icon: ChartColumn, managerOnly: true },
  { label: tr('设置'), to: '/settings', icon: Settings },
]
