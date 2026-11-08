/** 页面代码下载中的占位（通常一闪而过）。 */
export function PageLoading() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-label="加载中">
      <div className="size-7 animate-spin rounded-full border-2 border-slate-200 border-t-blue-600" />
    </div>
  )
}
