import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import { formatDay } from './format'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** 只显示日期（按界面语言）。 */
export function formatDate(iso: string | null | undefined): string {
  return formatDay(iso)
}
