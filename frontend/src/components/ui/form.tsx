import type { InputHTMLAttributes, ReactNode, Ref, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

const fieldBase =
  'block w-full rounded-lg border bg-white px-3 text-sm text-slate-900 shadow-sm transition-colors placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600/20 disabled:bg-slate-50 disabled:text-slate-500'

function borderFor(invalid?: boolean) {
  return invalid ? 'border-red-400 focus:border-red-500' : 'border-slate-300 focus:border-blue-600'
}

export function Input({
  className,
  invalid,
  ref,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean; ref?: Ref<HTMLInputElement> }) {
  return (
    <input
      ref={ref}
      className={cn(fieldBase, 'h-10', borderFor(invalid), className)}
      aria-invalid={invalid || undefined}
      {...props}
    />
  )
}

export function Select({
  className,
  invalid,
  ref,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean; ref?: Ref<HTMLSelectElement> }) {
  return (
    <select
      ref={ref}
      className={cn(fieldBase, 'h-10 pr-8', borderFor(invalid), className)}
      aria-invalid={invalid || undefined}
      {...props}
    >
      {children}
    </select>
  )
}

export function Textarea({
  className,
  invalid,
  ref,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean; ref?: Ref<HTMLTextAreaElement> }) {
  return (
    <textarea
      ref={ref}
      className={cn(fieldBase, 'min-h-20 py-2', borderFor(invalid), className)}
      aria-invalid={invalid || undefined}
      {...props}
    />
  )
}

/** 表单字段：标签 + 控件 + 提示/错误。 */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  required,
  children,
  className,
}: {
  label: string
  htmlFor: string
  error?: string
  hint?: ReactNode
  required?: boolean
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={htmlFor} className="block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="ml-0.5 text-red-500">*</span>}
      </label>
      {children}
      {error ? (
        <p className="text-xs text-red-600" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs text-slate-500">{hint}</p>
      ) : null}
    </div>
  )
}

export function FormError({ message }: { message?: string | null }) {
  if (!message) return null
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
      {message}
    </div>
  )
}
