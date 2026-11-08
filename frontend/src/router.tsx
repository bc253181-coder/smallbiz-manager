import { createBrowserRouter, Link, Navigate, Outlet, useLocation } from 'react-router'
import { AppShell } from '@/components/layout/AppShell'
import { FullPageSpinner } from '@/components/layout/AuthLayout'
import { useAuth } from '@/features/auth/AuthProvider'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { CategoriesPage } from '@/features/catalog/pages/CategoriesPage'
import { ProductDetailPage } from '@/features/catalog/pages/ProductDetailPage'
import { ProductFormPage } from '@/features/catalog/pages/ProductFormPage'
import { ProductListPage } from '@/features/catalog/pages/ProductListPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { InventoryPage } from '@/features/inventory/pages/InventoryPage'
import { MovementsPage } from '@/features/inventory/pages/MovementsPage'
import { CustomerDetailPage } from '@/features/partners/pages/CustomerDetailPage'
import { CustomerListPage } from '@/features/partners/pages/CustomerListPage'
import { SupplierDetailPage } from '@/features/partners/pages/SupplierDetailPage'
import { SupplierListPage } from '@/features/partners/pages/SupplierListPage'
import { PurchaseDetailPage } from '@/features/purchasing/pages/PurchaseDetailPage'
import { PurchaseFormPage } from '@/features/purchasing/pages/PurchaseFormPage'
import { PurchaseListPage } from '@/features/purchasing/pages/PurchaseListPage'
import { ReportsPage } from '@/features/reports/pages/ReportsPage'
import { SaleDetailPage } from '@/features/sales/pages/SaleDetailPage'
import { SaleFormPage } from '@/features/sales/pages/SaleFormPage'
import { SaleListPage } from '@/features/sales/pages/SaleListPage'
import { useCanManage } from '@/lib/hooks'
import { SettingsPage } from '@/features/settings/SettingsPage'
import { CreateStorePage } from '@/features/tenant/pages/CreateStorePage'
import { SelectStorePage } from '@/features/tenant/pages/SelectStorePage'

/** 未登录 → 去登录页（登录后回到原页面）。 */
function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullPageSpinner />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** 已登录但还没进入店铺 → 去创建或选择店铺。 */
function RequireTenant() {
  const { session } = useAuth()
  if (!session) return null
  if (!session.currentTenant) {
    return <Navigate to={session.tenants.length > 0 ? '/select-tenant' : '/onboarding'} replace />
  }
  return <Outlet />
}

/** 已登录用户访问登录/注册页 → 回首页。 */
function GuestOnly() {
  const { status } = useAuth()
  if (status === 'loading') return <FullPageSpinner />
  if (status === 'authenticated') return <Navigate to="/" replace />
  return <Outlet />
}

/** 只有店主/管理员能进入的页面（店员直接访问网址时回到上一级）。 */
function RequireManager() {
  const canManage = useCanManage()
  if (!canManage) return <Navigate to="/" replace />
  return <Outlet />
}

function OnboardingRoute() {
  const { session } = useAuth()
  // 已有店铺的用户不需要引导页
  if (session?.currentTenant) return <Navigate to="/" replace />
  return <CreateStorePage firstStore />
}

function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
      <p className="text-5xl font-bold text-slate-200">404</p>
      <p className="text-slate-600">页面不存在</p>
      <Link to="/" className="text-sm font-medium text-blue-600 hover:underline">
        返回首页
      </Link>
    </div>
  )
}

export const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      { path: '/onboarding', element: <OnboardingRoute /> },
      { path: '/select-tenant', element: <SelectStorePage /> },
      { path: '/stores/new', element: <CreateStorePage firstStore={false} /> },
      {
        element: <RequireTenant />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <DashboardPage /> },
              { path: '/products', element: <ProductListPage /> },
              { path: '/products/categories', element: <CategoriesPage /> },
              {
                element: <RequireManager />,
                children: [
                  { path: '/products/new', element: <ProductFormPage /> },
                  { path: '/products/:id/edit', element: <ProductFormPage /> },
                ],
              },
              { path: '/products/:id', element: <ProductDetailPage /> },
              { path: '/inventory', element: <InventoryPage /> },
              { path: '/inventory/movements', element: <MovementsPage /> },
              { path: '/sales', element: <SaleListPage /> },
              { path: '/sales/new', element: <SaleFormPage /> },
              { path: '/sales/:id', element: <SaleDetailPage /> },
              { path: '/customers', element: <CustomerListPage /> },
              { path: '/customers/:id', element: <CustomerDetailPage /> },
              {
                element: <RequireManager />,
                children: [
                  { path: '/suppliers', element: <SupplierListPage /> },
                  { path: '/suppliers/:id', element: <SupplierDetailPage /> },
                  { path: '/reports', element: <ReportsPage /> },
                  { path: '/purchases', element: <PurchaseListPage /> },
                  { path: '/purchases/new', element: <PurchaseFormPage /> },
                  { path: '/purchases/:id', element: <PurchaseDetailPage /> },
                  { path: '/purchases/:id/edit', element: <PurchaseFormPage /> },
                ],
              },
              { path: '/settings', element: <SettingsPage /> },
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
])
