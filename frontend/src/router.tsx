import { createBrowserRouter, Link, Navigate, Outlet, useLocation } from 'react-router'
import { AppShell } from '@/components/layout/AppShell'
import { FullPageSpinner } from '@/components/layout/AuthLayout'
import { useAuth } from '@/features/auth/AuthProvider'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { AdminLayout } from '@/features/admin/AdminLayout'
import { RequireFeature } from '@/features/billing/FeatureGate'
import { PrivacyPage, TermsPage } from '@/features/legal/LegalPages'
import { InvitePage } from '@/features/staff/InvitePage'
import { useCanManage } from '@/lib/hooks'
import { CreateStorePage } from '@/features/tenant/pages/CreateStorePage'
import { SelectStorePage } from '@/features/tenant/pages/SelectStorePage'
import {
  AdminCodesPage,
  AdminConfigPage,
  AdminOverviewPage,
  AdminPaymentsPage,
  AdminSupportPage,
  AdminTenantsPage,
  BillingPage,
  CategoriesPage,
  CustomerDetailPage,
  CustomerListPage,
  DashboardPage,
  InventoryPage,
  MovementsPage,
  PaymentPage,
  ProductDetailPage,
  ProductFormPage,
  ProductListPage,
  PurchaseDetailPage,
  PurchaseFormPage,
  PurchaseListPage,
  ReceivablesPage,
  ReceivablePaymentsPage,
  ReportsPage,
  StatementPage,
  SaleDetailPage,
  SaleFormPage,
  SaleListPage,
  SettingsPage,
  StaffPage,
  SupplierDetailPage,
  SupplierListPage,
} from '@/lazyPages'

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

/** 已登录用户访问登录/注册页 → 回到原页面（例如员工邀请页），否则回首页。 */
function GuestOnly() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullPageSpinner />
  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/'} replace />
  }
  return <Outlet />
}

/** 只有店主能进入的页面。 */
function RequireOwner() {
  const { session } = useAuth()
  if (session?.currentTenant?.role !== 'OWNER') return <Navigate to="/settings" replace />
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
  // 公开页面：登录与否都能访问
  { path: '/terms', element: <TermsPage /> },
  { path: '/privacy', element: <PrivacyPage /> },
  { path: '/invite/:token', element: <InvitePage /> },
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
        path: '/admin',
        element: <AdminLayout />,
        children: [
          { index: true, element: <AdminOverviewPage /> },
          { path: 'payments', element: <AdminPaymentsPage /> },
          { path: 'tenants', element: <AdminTenantsPage /> },
          { path: 'codes', element: <AdminCodesPage /> },
          { path: 'config', element: <AdminConfigPage /> },
          { path: 'support', element: <AdminSupportPage /> },
        ],
      },
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
              {
                element: <RequireFeature feature="CUSTOMERS" />,
                children: [
                  { path: '/customers', element: <CustomerListPage /> },
                  { path: '/customers/:id', element: <CustomerDetailPage /> },
                ],
              },
              {
                element: <RequireManager />,
                children: [
                  {
                    element: <RequireFeature feature="SUPPLIERS" />,
                    children: [
                      { path: '/suppliers', element: <SupplierListPage /> },
                      { path: '/suppliers/:id', element: <SupplierDetailPage /> },
                    ],
                  },
                  { path: '/reports', element: <ReportsPage /> },
                  {
                    element: <RequireFeature feature="RECEIVABLES" />,
                    children: [
                      { path: '/receivables', element: <ReceivablesPage /> },
                      { path: '/receivables/payments', element: <ReceivablePaymentsPage /> },
                      { path: '/customers/:id/statement', element: <StatementPage /> },
                    ],
                  },
                  {
                    element: <RequireFeature feature="PURCHASING" />,
                    children: [
                      { path: '/purchases', element: <PurchaseListPage /> },
                      { path: '/purchases/new', element: <PurchaseFormPage /> },
                      { path: '/purchases/:id', element: <PurchaseDetailPage /> },
                      { path: '/purchases/:id/edit', element: <PurchaseFormPage /> },
                    ],
                  },
                ],
              },
              { path: '/settings', element: <SettingsPage /> },
              { path: '/settings/billing', element: <BillingPage /> },
              { element: <RequireOwner />, children: [{ path: '/settings/staff', element: <StaffPage /> }] },
              { path: '/settings/billing/pay/:id', element: <PaymentPage /> },
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
])
