import {
  ChartColumn,
  Contact,
  Factory,
  LayoutDashboard,
  Package,
  ReceiptText,
  Settings,
  Truck,
  Warehouse,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  /** 尚未上线的模块：显示但不可点击，并标注上线阶段。 */
  comingSoon?: boolean
  /** 仅店主/管理员可见。 */
  managerOnly?: boolean
}

export const NAV_ITEMS: NavItem[] = [
  { label: '仪表盘', to: '/', icon: LayoutDashboard },
  { label: '商品', to: '/products', icon: Package },
  { label: '库存', to: '/inventory', icon: Warehouse },
  { label: '销售', to: '/sales', icon: ReceiptText },
  { label: '采购', to: '/purchases', icon: Truck, managerOnly: true },
  { label: '客户', to: '/customers', icon: Contact },
  { label: '供应商', to: '/suppliers', icon: Factory, managerOnly: true },
  { label: '报表', to: '/reports', icon: ChartColumn, managerOnly: true },
  { label: '设置', to: '/settings', icon: Settings },
]
