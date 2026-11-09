import { useMutation, useQuery } from '@tanstack/react-query'
import { Copy, MessageCircleQuestion, SendHorizontal, ThumbsDown, ThumbsUp, UserRound, X } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { cn } from '@/lib/utils'

import { getLang, tr } from '@/lib/i18n'
interface Handoff {
  wechatId: string
  tip: string
}

interface Answer {
  matched: boolean
  faqId: number | null
  question: string | null
  answer: string | null
  actionLabel: string | null
  actionPath: string | null
  handoff: Handoff | null
  source?: 'KEYWORD' | 'AI'
  logId?: number | null
}

interface Welcome {
  greeting: string
  quickReplies: { id: number; question: string }[]
  ai: boolean
}

interface HistoryItem {
  role: 'user' | 'bot'
  text: string
}

type NewMessage = { role: 'user'; text: string } | { role: 'bot'; answer: Answer; feedback?: 'up' | 'down' }
type Message = NewMessage & { id: number }

const supportApi = {
  welcome: () => api.get<Welcome>('/support/welcome', { params: { lang: getLang() } }).then((r) => r.data),
  ask: (message: string, history: HistoryItem[]) =>
    api.post<Answer>('/support/ask', { message, history, lang: getLang() }).then((r) => r.data),
  faq: (id: number) => api.get<Answer>(`/support/faqs/${id}`).then((r) => r.data),
  contact: () => api.get<Handoff>('/support/contact', { params: { lang: getLang() } }).then((r) => r.data),
  feedback: (id: number, helpful: boolean) => api.post(`/support/faqs/${id}/feedback`, { helpful }),
  aiFeedback: (logId: number, helpful: boolean) => api.post(`/support/ai/${logId}/feedback`, { helpful }),
}

let nextId = 1
const HUMAN = tr('人工客服')

/** 右下角客服：固定话术自动回答，答不上来时给出客服微信。 */
export function SupportWidget({ withTabBar }: { withTabBar?: boolean }) {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const listRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  const welcome = useQuery({ queryKey: ['support', 'welcome', getLang()], queryFn: supportApi.welcome, enabled: open, staleTime: 600_000 })
  const ai = welcome.data?.ai === true

  const push = (m: NewMessage) => setMessages((ms) => [...ms, { ...m, id: nextId++ }])
  const reply = useMutation({
    mutationFn: (fn: () => Promise<Answer>) => fn(),
    onSuccess: (answer) => push({ role: 'bot', answer }),
    onError: (e) => toast.error(errorMessage(e, tr('客服暂时无法回答，请稍后再试'))),
  })

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages, reply.isPending])

  function ask(text: string) {
    const q = text.trim()
    if (!q || reply.isPending) return
    // 最近几轮对话（只在内存里，关闭页面即清空），让 AI 能理解追问
    const history = messages
      .slice(-6)
      .map((m): HistoryItem | null =>
        m.role === 'user' ? { role: 'user', text: m.text } : m.answer.answer ? { role: 'bot', text: m.answer.answer } : null,
      )
      .filter((h): h is HistoryItem => h !== null)
    push({ role: 'user', text: q })
    setInput('')
    reply.mutate(() => supportApi.ask(q.slice(0, 300), history))
  }

  function quick(id: number, question: string) {
    if (reply.isPending) return
    push({ role: 'user', text: question })
    reply.mutate(() => supportApi.faq(id))
  }

  function human() {
    if (reply.isPending) return
    push({ role: 'user', text: tr('联系人工客服') })
    reply.mutate(async () => {
      const handoff = await supportApi.contact()
      return { matched: false, faqId: null, question: HUMAN, answer: null, actionLabel: null, actionPath: null, handoff }
    })
  }

  function feedback(m: Message & { role: 'bot' }, helpful: boolean) {
    const { faqId, logId } = m.answer
    if ((!faqId && !logId) || m.feedback) return
    setMessages((ms) => ms.map((x) => (x.id === m.id ? { ...x, feedback: helpful ? 'up' : 'down' } : x)))
    const req = logId ? supportApi.aiFeedback(logId, helpful) : supportApi.feedback(faqId!, helpful)
    req.catch(() => undefined)
  }

  return (
    <div className="print:hidden">
      {open && (
        <div
          role="dialog"
          aria-label={tr('在线客服')}
          className={cn(
            'fixed right-4 z-40 flex h-[min(34rem,72dvh)] w-[min(24rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl',
            withTabBar ? 'bottom-36 lg:bottom-24' : 'bottom-24',
          )}
        >
          <div className="flex items-center justify-between border-b border-slate-100 bg-blue-600 px-4 py-3 text-white">
            <div>
              <p className="text-sm font-semibold">{ai ? tr('SmallBiz AI 小助手') : tr('SmallBiz 小助手')}</p>
              <p className="text-xs text-blue-100">{ai ? tr('根据帮助文档回答 · 答不上来会转人工') : tr('自动回复 · 答不上来会转人工')}</p>
            </div>
            <button type="button" onClick={() => setOpen(false)} className="rounded-lg p-1.5 hover:bg-white/15" aria-label={tr('关闭客服')}>
              <X className="size-5" />
            </button>
          </div>

          <div ref={listRef} className="flex-1 space-y-3 overflow-y-auto bg-slate-50 p-4" aria-live="polite">
            {welcome.data && (
              <BotBubble>
                <p>{welcome.data.greeting}</p>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {welcome.data.quickReplies.map((q) => (
                    <button
                      key={q.id}
                      type="button"
                      onClick={() => quick(q.id, q.question)}
                      className="rounded-full border border-blue-200 bg-white px-2.5 py-1 text-xs text-blue-700 hover:bg-blue-50"
                    >
                      {q.question}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={human}
                    className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs text-slate-600 hover:bg-slate-100"
                  >
                    {tr('人工客服')}
                  </button>
                </div>
              </BotBubble>
            )}
            {welcome.isLoading && <BotBubble>…</BotBubble>}
            {welcome.isError && <BotBubble>{tr('客服加载失败，请检查网络后重新打开。')}</BotBubble>}

            {messages.map((m) =>
              m.role === 'user' ? (
                <div key={m.id} className="flex justify-end">
                  <p className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-sm bg-blue-600 px-3 py-2 text-sm text-white">{m.text}</p>
                </div>
              ) : (
                <BotBubble key={m.id}>
                  {m.answer.answer && <p className="whitespace-pre-line">{m.answer.answer}</p>}
                  {m.answer.actionPath && m.answer.actionLabel && (
                    <button
                      type="button"
                      onClick={() => {
                        navigate(m.answer.actionPath!)
                        setOpen(false)
                      }}
                      className="mt-2 inline-flex rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100"
                    >
                      {tr(m.answer.actionLabel)} →
                    </button>
                  )}
                  {m.answer.handoff && <HandoffCard handoff={m.answer.handoff} unmatched={m.answer.question !== HUMAN && !m.answer.answer} />}
                  {m.answer.source === 'AI' && m.answer.matched && <p className="mt-1.5 text-[11px] text-slate-400">{tr('AI 生成，仅供参考')}</p>}
                  {(m.answer.faqId || (m.answer.logId && m.answer.matched)) && (
                    <div className="mt-2 flex items-center gap-1 text-xs text-slate-400">
                      {m.feedback ? (
                        <span>{m.feedback === 'up' ? tr('感谢反馈！') : tr('抱歉没帮上忙，可以点「人工客服」')}</span>
                      ) : (
                        <>
                          {tr('有帮助吗？')}
                          <button type="button" className="rounded p-1 hover:bg-slate-100 hover:text-slate-600" aria-label={tr('有帮助')} onClick={() => feedback(m, true)}>
                            <ThumbsUp className="size-3.5" />
                          </button>
                          <button type="button" className="rounded p-1 hover:bg-slate-100 hover:text-slate-600" aria-label={tr('没帮助')} onClick={() => feedback(m, false)}>
                            <ThumbsDown className="size-3.5" />
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </BotBubble>
              ),
            )}
            {reply.isPending && <BotBubble>{ai ? tr('正在思考…') : tr('正在查找答案…')}</BotBubble>}
          </div>

          <form
            className="flex items-center gap-2 border-t border-slate-100 p-3"
            onSubmit={(e) => {
              e.preventDefault()
              ask(input)
            }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              maxLength={300}
              placeholder={tr('输入你的问题，例如：怎么开单')}
              aria-label={tr('输入问题')}
              className="h-10 min-w-0 flex-1 rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
            <button
              type="submit"
              disabled={!input.trim() || reply.isPending}
              className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
              aria-label={tr('发送')}
            >
              <SendHorizontal className="size-4" />
            </button>
          </form>
        </div>
      )}

      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-label={open ? tr('关闭客服') : tr('打开在线客服')}
        className={cn(
          'fixed right-4 z-40 flex size-13 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg hover:bg-blue-700',
          withTabBar ? 'bottom-20 lg:bottom-6' : 'bottom-6',
        )}
      >
        {open ? <X className="size-6" /> : <MessageCircleQuestion className="size-6" />}
      </button>
    </div>
  )
}

function BotBubble({ children }: { children: ReactNode }) {
  return (
    <div className="max-w-[90%] rounded-2xl rounded-bl-sm border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm">
      {children}
    </div>
  )
}

function HandoffCard({ handoff, unmatched }: { handoff: Handoff; unmatched: boolean }) {
  async function copy() {
    try {
      await navigator.clipboard.writeText(handoff.wechatId)
      toast.success(tr('微信号已复制'))
    } catch {
      toast.error(tr('复制失败，请手动输入'))
    }
  }
  return (
    <div>
      <p>{unmatched ? tr('这个问题我还答不好，') : ''}{tr('可以添加客服微信，由人工为你解答：')}</p>
      <p className="mt-1 text-xs text-slate-500">{handoff.tip}</p>
      <div className="mt-2 flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2">
        <UserRound className="size-4 shrink-0 text-emerald-700" aria-hidden />
        <span className="min-w-0 flex-1">
          <span className="block text-xs text-emerald-700">{tr('客服微信')}</span>
          <span className="block break-all font-mono font-semibold text-emerald-900">{handoff.wechatId}</span>
        </span>
        <button type="button" onClick={() => void copy()} className="rounded p-1.5 text-emerald-700 hover:bg-emerald-100" aria-label={tr('复制微信号')}>
          <Copy className="size-4" />
        </button>
      </div>
    </div>
  )
}
