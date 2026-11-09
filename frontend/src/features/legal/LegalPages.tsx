import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Brand } from '@/components/layout/Brand'
import { getLang } from '@/lib/i18n'
import { PrivacyContentEn, TermsContentEn } from './LegalPagesEn'

/** 与后端 LegalTerms.CURRENT_VERSION 保持一致；修改协议内容时两边一起更新。 */
export const LEGAL_VERSION = '2026-11-09'
const SUPPORT_WECHAT = 'Pianzibudehaosi_1A'

const EN = () => getLang() === 'en'

function LegalLayout({ title, children }: { title: string; children: ReactNode }) {
  const en = EN()
  return (
    <div className="min-h-dvh bg-slate-50 px-4 py-8">
      <article className="mx-auto max-w-3xl rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-10">
        <Link to="/" aria-label={en ? 'Home' : '返回首页'}>
          <Brand />
        </Link>
        <h1 className="mt-8 text-2xl font-semibold text-slate-900">{title}</h1>
        <p className="mt-1 text-sm text-slate-500">
          {en ? 'Version: ' : '版本日期：'}
          {LEGAL_VERSION}
          {en && ' · In case of any difference, the Chinese version prevails.'}
        </p>
        <div className="mt-6 space-y-6 text-sm leading-7 text-slate-700 [&_h2]:mb-2 [&_h2]:text-base [&_h2]:font-semibold [&_h2]:text-slate-900 [&_li]:ml-5 [&_li]:list-disc">
          {children}
        </div>
        <p className="mt-10 flex flex-wrap gap-4 border-t border-slate-100 pt-6 text-sm">
          <Link to="/terms" className="text-blue-600 hover:underline">
            {en ? 'Terms of Service' : '用户协议'}
          </Link>
          <Link to="/privacy" className="text-blue-600 hover:underline">
            {en ? 'Privacy Policy' : '隐私政策'}
          </Link>
          <Link to="/register" className="text-blue-600 hover:underline">
            {en ? 'Back to sign up' : '返回注册'}
          </Link>
        </p>
      </article>
    </div>
  )
}

export function TermsPage() {
  if (EN()) {
    return (
      <LegalLayout title="SmallBiz Manager Terms of Service">
        <TermsContentEn />
      </LegalLayout>
    )
  }
  return (
    <LegalLayout title="SmallBiz Manager 用户协议">
      <p>
        欢迎使用 SmallBiz Manager（以下简称“本服务”）。本服务是面向小商户的在线进销存与经营管理工具。注册即表示你已阅读并同意本协议和《
        <Link to="/privacy" className="text-blue-600 hover:underline">
          隐私政策
        </Link>
        》。
      </p>

      <section>
        <h2>一、账号</h2>
        <ul>
          <li>你需要使用真实可用的邮箱注册，并妥善保管密码。通过你的账号进行的操作视为你本人的操作。</li>
          <li>一个账号可以创建或加入多个店铺。店主对店铺内的数据和成员负责。</li>
          <li>发现账号被盗用，请立即修改密码并联系我们。修改密码后，所有设备都会退出登录。</li>
        </ul>
      </section>

      <section>
        <h2>二、套餐与付费</h2>
        <ul>
          <li>免费版长期可用，有功能和数量限制（例如最多 100 个商品）。标准版等付费套餐的价格和权益以「套餐与续费」页面显示为准。</li>
          <li>付费方式：在本网站扫码付款并上传付款截图，经人工核对后开通；或使用激活码开通。套餐按店铺计算，开通后立即生效。</li>
          <li>首月体验每个店铺限一次。套餐不会自动续费，到期前请自行续费。</li>
          <li>套餐到期后，店铺自动回到免费版：已有数据会完整保留，只是付费功能暂时不能使用；续费后即可继续使用。</li>
          <li>因我们的原因导致服务长时间无法使用的，你可以联系客服，我们会通过延长套餐时长等方式补偿。其他退款诉求请联系客服协商处理。</li>
          <li>激活码仅能使用一次，请勿转售或公开。作废、被盗用或来源不明的激活码，我们有权拒绝兑换。</li>
        </ul>
      </section>

      <section>
        <h2>三、你的数据</h2>
        <ul>
          <li>你在店铺中录入的商品、库存、客户、供应商、订单等经营数据归你（店铺）所有。我们只为提供本服务而存储和处理这些数据，不会出售，也不会用于其他目的。</li>
          <li>每个店铺的数据相互隔离，其他店铺无法看到。</li>
          <li>你录入的客户、供应商信息可能包含他人的个人信息，请确保你已依法获得并有权使用这些信息。</li>
          <li>你可以随时导出报表。如需注销账号或删除店铺数据，请联系客服。</li>
        </ul>
      </section>

      <section>
        <h2>四、使用规范</h2>
        <p>你不得利用本服务从事违法活动，不得记录违法交易，不得尝试访问其他店铺的数据、攻击或干扰本服务，不得以自动化方式大量注册或调用接口。违反的，我们可以暂停或终止你的账号。</p>
      </section>

      <section>
        <h2>五、服务的变更与中断</h2>
        <ul>
          <li>我们会尽力保证服务稳定并定期备份数据，但不能保证服务永不中断或没有错误。系统维护会尽量提前通知。</li>
          <li>本服务的统计数字（如毛利、库存价值）依据你录入的数据计算，仅供经营参考，不构成财务或税务意见。</li>
          <li>在法律允许的范围内，我们对因使用本服务而产生的间接损失不承担责任。</li>
        </ul>
      </section>

      <section>
        <h2>六、协议更新</h2>
        <p>我们可能会更新本协议。重要变更会在网站内提示；更新后继续使用本服务，即表示你同意更新后的协议。</p>
      </section>

      <section>
        <h2>七、联系我们</h2>
        <p>
          可以通过网站右下角的在线客服联系我们，或添加客服微信 <span className="font-mono">{SUPPORT_WECHAT}</span>。
        </p>
      </section>
    </LegalLayout>
  )
}

export function PrivacyPage() {
  if (EN()) {
    return (
      <LegalLayout title="SmallBiz Manager Privacy Policy">
        <PrivacyContentEn />
      </LegalLayout>
    )
  }
  return (
    <LegalLayout title="SmallBiz Manager 隐私政策">
      <p>本政策说明我们收集哪些信息、为什么收集，以及如何保护这些信息。我们只收集提供服务所必需的信息。</p>

      <section>
        <h2>一、我们收集的信息</h2>
        <ul>
          <li>
            <strong>账号信息</strong>：邮箱、昵称、密码（只保存加密后的摘要，任何人都无法查看原密码），以及你选填的手机号。
          </li>
          <li>
            <strong>店铺资料</strong>：店铺名称、经营类型、时区，以及你选填的联系电话和地址。
          </li>
          <li>
            <strong>经营数据</strong>：你录入的商品、库存、客户、供应商、采购单和销售单等。其中客户、供应商的联系方式由你录入，我们代你存储和处理。
          </li>
          <li>
            <strong>付款信息</strong>：扫码付款时你上传的付款截图、交易单号和留言，仅用于核对到账。我们不会收集你的银行卡号或支付密码。
          </li>
          <li>
            <strong>客服对话</strong>：客服机器人回答不了的问题文字会被记录，用于改进回答。记录中不包含你的账号信息。
          </li>
          <li>
            <strong>技术信息</strong>：访问时的 IP 地址，用于防止暴力破解密码和滥用接口（例如限制登录失败次数）。
          </li>
        </ul>
      </section>

      <section>
        <h2>二、Cookie</h2>
        <p>我们只使用一个必要的 Cookie 来保持登录状态（30 天内免登录），网页脚本无法读取它。我们不使用广告 Cookie，也不使用第三方统计或追踪工具。</p>
      </section>

      <section>
        <h2>三、我们如何使用和共享信息</h2>
        <ul>
          <li>仅用于提供、维护和改进本服务，处理付款和客服请求。</li>
          <li>我们不会出售你的信息，也不会提供给第三方用于营销。</li>
          <li>数据存储在我们租用的云服务器上。除法律法规要求外，我们不会向其他人披露你的信息。</li>
          <li>平台管理员只能看到店铺名称、套餐状态和店主的邮箱与昵称（用于处理付款和客服），不会查看店铺的经营数据。</li>
        </ul>
      </section>

      <section>
        <h2>四、保存期限与安全</h2>
        <ul>
          <li>账号存续期间我们会保存你的数据；套餐到期不会删除数据。注销账号后，我们会在合理期限内删除或匿名化你的数据（法律要求保留的除外）。</li>
          <li>安全措施包括：全站 HTTPS 加密传输、密码不可逆加密、店铺之间数据隔离、付款截图不对外公开、定期备份。</li>
        </ul>
      </section>

      <section>
        <h2>五、你的权利</h2>
        <p>你可以在「设置」中查看和修改个人资料与店铺资料。如需查询、导出、更正或删除你的个人信息，或注销账号，请联系客服，我们会在 15 个工作日内处理。</p>
      </section>

      <section>
        <h2>六、未成年人</h2>
        <p>本服务面向商户经营者，不面向未满 18 周岁的未成年人。</p>
      </section>

      <section>
        <h2>七、政策更新与联系方式</h2>
        <p>
          本政策更新时会在网站内提示。如有疑问，请通过网站右下角的在线客服联系我们，或添加客服微信{' '}
          <span className="font-mono">{SUPPORT_WECHAT}</span>。
        </p>
      </section>
    </LegalLayout>
  )
}
