import { lazy, type ComponentType } from 'react'

/**
 * 按页面拆分代码：只有打开某个页面时才下载它的代码（图表库等较大的依赖不会拖慢首屏）。
 * 页面文件使用具名导出，这里转换成 React.lazy 需要的 default。
 */
function page<M extends Record<string, unknown>, K extends keyof M>(load: () => Promise<M>, key: K) {
  return lazy(async () => ({ default: (await load())[key] as ComponentType }))
}

export const DashboardPage = page(() => import('@/features/dashboard/DashboardPage'), 'DashboardPage')
export const ProductListPage = page(() => import('@/features/catalog/pages/ProductListPage'), 'ProductListPage')
export const ProductDetailPage = page(() => import('@/features/catalog/pages/ProductDetailPage'), 'ProductDetailPage')
export const ProductFormPage = page(() => import('@/features/catalog/pages/ProductFormPage'), 'ProductFormPage')
export const LabelsPage = page(() => import('@/features/barcode/LabelsPage'), 'LabelsPage')
export const CategoriesPage =page(() => import('@/features/catalog/pages/CategoriesPage'), 'CategoriesPage')
export const InventoryPage = page(() => import('@/features/inventory/pages/InventoryPage'), 'InventoryPage')
export const MovementsPage = page(() => import('@/features/inventory/pages/MovementsPage'), 'MovementsPage')
export const SaleListPage = page(() => import('@/features/sales/pages/SaleListPage'), 'SaleListPage')
export const SaleFormPage = page(() => import('@/features/sales/pages/SaleFormPage'), 'SaleFormPage')
export const SaleDetailPage = page(() => import('@/features/sales/pages/SaleDetailPage'), 'SaleDetailPage')
export const CustomerListPage = page(() => import('@/features/partners/pages/CustomerListPage'), 'CustomerListPage')
export const CustomerDetailPage = page(() => import('@/features/partners/pages/CustomerDetailPage'), 'CustomerDetailPage')
export const SupplierListPage = page(() => import('@/features/partners/pages/SupplierListPage'), 'SupplierListPage')
export const SupplierDetailPage = page(() => import('@/features/partners/pages/SupplierDetailPage'), 'SupplierDetailPage')
export const PurchaseListPage = page(() => import('@/features/purchasing/pages/PurchaseListPage'), 'PurchaseListPage')
export const PurchaseFormPage = page(() => import('@/features/purchasing/pages/PurchaseFormPage'), 'PurchaseFormPage')
export const PurchaseDetailPage = page(() => import('@/features/purchasing/pages/PurchaseDetailPage'), 'PurchaseDetailPage')
export const ReportsPage = page(() => import('@/features/reports/pages/ReportsPage'), 'ReportsPage')
export const SettingsPage = page(() => import('@/features/settings/SettingsPage'), 'SettingsPage')
export const BillingPage = page(() => import('@/features/billing/pages/BillingPage'), 'BillingPage')
export const WarehousesPage = page(() => import('@/features/warehouse/WarehousesPage'), 'WarehousesPage')
export const TransfersPage = page(() => import('@/features/warehouse/TransferPages'), 'TransfersPage')
export const TransferFormPage = page(() => import('@/features/warehouse/TransferPages'), 'TransferFormPage')
export const TransferDetailPage = page(() => import('@/features/warehouse/TransferPages'), 'TransferDetailPage')
export const StaffPage = page(() => import('@/features/staff/StaffPage'), 'StaffPage')
export const PaymentPage =page(() => import('@/features/billing/pages/PaymentPage'), 'PaymentPage')
// 模拟支付页面只在开发环境构建（正式构建中这段代码被整体去掉）
export const MockCheckoutPage = import.meta.env.DEV
  ? page(() => import('@/features/billing/pages/MockCheckoutPage'), 'MockCheckoutPage')
  : () => null
export const ReceivablesPage = page(() => import('@/features/receivables/pages/ReceivablesPage'), 'ReceivablesPage')
export const ReceivablePaymentsPage = page(() => import('@/features/receivables/pages/PaymentsPage'), 'PaymentsPage')
export const StatementPage = page(() => import('@/features/receivables/pages/StatementPage'), 'StatementPage')
export const AdminOverviewPage = page(() => import('@/features/admin/pages/AdminTenantsPages'), 'AdminOverviewPage')
export const AdminTenantsPage = page(() => import('@/features/admin/pages/AdminTenantsPages'), 'AdminTenantsPage')
export const AdminPaymentsPage = page(() => import('@/features/admin/pages/AdminPaymentsPage'), 'AdminPaymentsPage')
export const AdminCodesPage = page(() => import('@/features/admin/pages/AdminCodesPage'), 'AdminCodesPage')
export const AdminDispenserPage = page(() => import('@/features/admin/pages/AdminDispenserPage'), 'AdminDispenserPage')
export const QuickCodePage = page(() => import('@/features/admin/QuickCodePage'), 'QuickCodePage')
export const AdminConfigPage = page(() => import('@/features/admin/pages/AdminConfigPage'), 'AdminConfigPage')
export const AdminSupportPage = page(() => import('@/features/admin/pages/AdminSupportPage'), 'AdminSupportPage')
