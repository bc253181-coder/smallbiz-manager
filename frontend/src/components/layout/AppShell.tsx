import { Menu, X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import { useState } from 'react'
import { Outlet } from 'react-router'
import { Brand } from './Brand'
import { LowStockBell } from './LowStockBell'
import { SidebarNav } from './SidebarNav'
import { StoreSwitcher } from './StoreSwitcher'
import { UserMenu } from './UserMenu'

/** 登录后的主界面框架：桌面左侧导航；手机/平板为抽屉菜单。 */
export function AppShell() {
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    <div className="min-h-dvh lg:pl-64">
      {/* 桌面侧边栏 */}
      <aside className="fixed inset-y-0 left-0 hidden w-64 flex-col border-r border-slate-200 bg-white lg:flex">
        <div className="flex h-16 items-center border-b border-slate-100 px-5">
          <Brand />
        </div>
        <div className="flex-1 overflow-y-auto p-3">
          <SidebarNav />
        </div>
      </aside>

      {/* 手机/平板抽屉 */}
      <Dialog.Root open={drawerOpen} onOpenChange={setDrawerOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-900/40 lg:hidden" />
          <Dialog.Content
            className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[85vw] flex-col bg-white shadow-xl lg:hidden"
            aria-describedby={undefined}
          >
            <div className="flex h-16 items-center justify-between border-b border-slate-100 px-4">
              <Dialog.Title asChild>
                <div>
                  <Brand />
                </div>
              </Dialog.Title>
              <Dialog.Close className="rounded-lg p-2 text-slate-500 hover:bg-slate-100" aria-label="关闭菜单">
                <X className="size-5" />
              </Dialog.Close>
            </div>
            <div className="flex-1 overflow-y-auto p-3">
              <SidebarNav onNavigate={() => setDrawerOpen(false)} />
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* 顶栏 */}
      <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6">
        <button
          type="button"
          className="-ml-2 rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
          onClick={() => setDrawerOpen(true)}
          aria-label="打开菜单"
        >
          <Menu className="size-5" />
        </button>
        <StoreSwitcher />
        <div className="flex-1" />
        <LowStockBell />
        <UserMenu />
      </header>

      <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        <Outlet />
      </main>
    </div>
  )
}
