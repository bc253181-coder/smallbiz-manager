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
}

export const NAV_ITEMS: NavItem[] = [
  { label: '仪表盘', to: '/', icon: LayoutDashboard },
  { label: '商品', to: '/products', icon: Package, comingSoon: true },
  { label: '库存', to: '/inventory', icon: Warehouse, comingSoon: true },
  { label: '销售', to: '/sales', icon: ReceiptText, comingSoon: true },
  { label: '采购', to: '/purchases', icon: Truck, comingSoon: true },
  { label: '客户', to: '/customers', icon: Contact, comingSoon: true },
  { label: '供应商', to: '/suppliers', icon: Factory, comingSoon: true },
  { label: '报表', to: '/reports', icon: ChartColumn, comingSoon: true },
  { label: '设置', to: '/settings', icon: Settings },
]
