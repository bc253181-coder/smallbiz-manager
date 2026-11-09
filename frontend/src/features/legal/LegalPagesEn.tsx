import { Link } from 'react-router'

const SUPPORT_WECHAT = 'Pianzibudehaosi_1A'

/** English Terms of Service (same content as the Chinese version; the Chinese version prevails if they differ). */
export function TermsContentEn() {
  return (
    <>
      <p>
        Welcome to SmallBiz Manager (“the Service”), an online inventory, sales and business management tool for small
        merchants. By signing up you confirm that you have read and agree to these Terms and our{' '}
        <Link to="/privacy" className="text-blue-600 hover:underline">
          Privacy Policy
        </Link>
        .
      </p>

      <section>
        <h2>1. Your account</h2>
        <ul>
          <li>Sign up with a real email address you can access and keep your password safe. Actions taken with your account are treated as yours.</li>
          <li>One account can create or join several stores. The store owner is responsible for the store's data and members.</li>
          <li>If your account is misused, change your password immediately and contact us. Changing your password signs you out on all devices.</li>
        </ul>
      </section>

      <section>
        <h2>2. Membership and payment</h2>
        <ul>
          <li>The Free membership costs nothing and has feature and usage limits (for example how many stores and products you can create), as shown under “Settings → Membership”. Usage is counted per account; deleting products or stores doesn’t give it back.</li>
          <li>Lifetime Premium is a one-time purchase; its price and features are as shown on the Membership page. Once activated, all stores you own get Premium forever, with no automatic renewal or charges.</li>
          <li>To activate, buy an activation code through channels such as Xiaohongshu and redeem it on this website; it takes effect immediately. This website does not take payments directly.</li>
          <li>Earlier Standard and Pro subscriptions keep working until they expire, after which you become a Free member. Your existing data is always kept and is never deleted or locked because of your membership.</li>
          <li>If the Service is unavailable for a long time because of us, contact support and we will agree on compensation. For refunds, please contact the shop where you bought the code, or our support.</li>
          <li>Activation codes can be used only once — do not resell or publish them. We may refuse codes that are voided, stolen or of unknown origin.</li>
        </ul>
      </section>

      <section>
        <h2>3. Your data</h2>
        <ul>
          <li>Business data you enter — products, stock, customers, suppliers, orders — belongs to you (the store). We store and process it only to provide the Service, and never sell it or use it for other purposes.</li>
          <li>Each store's data is isolated and cannot be seen by other stores.</li>
          <li>Customer and supplier details you enter may include other people's personal information. Make sure you obtained it lawfully and have the right to use it.</li>
          <li>You can export reports at any time. To close your account or delete store data, please contact support.</li>
        </ul>
      </section>

      <section>
        <h2>4. Acceptable use</h2>
        <p>You must not use the Service for illegal activities or to record illegal transactions, try to access other stores' data, attack or interfere with the Service, or register accounts or call the API in bulk by automated means. We may suspend or terminate accounts that break these rules.</p>
      </section>

      <section>
        <h2>5. Changes and interruptions</h2>
        <ul>
          <li>We do our best to keep the Service stable and back up data regularly, but cannot guarantee it will never be interrupted or error-free. We try to announce maintenance in advance.</li>
          <li>Figures such as gross profit and stock value are calculated from the data you enter and are for business reference only, not financial or tax advice.</li>
          <li>To the extent permitted by law, we are not liable for indirect losses arising from use of the Service.</li>
        </ul>
      </section>

      <section>
        <h2>6. Updates to these Terms</h2>
        <p>We may update these Terms. We will notify you of important changes on the website; continuing to use the Service after an update means you accept the updated Terms.</p>
      </section>

      <section>
        <h2>7. Contact us</h2>
        <p>
          Use the help button in the bottom-right corner of the website, or add our support WeChat{' '}
          <span className="font-mono">{SUPPORT_WECHAT}</span>.
        </p>
      </section>
    </>
  )
}

/** English Privacy Policy. */
export function PrivacyContentEn() {
  return (
    <>
      <p>This policy explains what information we collect, why, and how we protect it. We collect only what is needed to provide the Service.</p>

      <section>
        <h2>1. Information we collect</h2>
        <ul>
          <li>
            <strong>Account information</strong>: email, name, password (we store only an irreversible hash — nobody can see your password), and an optional mobile number.
          </li>
          <li>
            <strong>Store details</strong>: store name, business type, time zone, currency, and optional phone and address.
          </li>
          <li>
            <strong>Business data</strong>: products, stock, customers, suppliers, purchase and sales orders you enter. Customer and supplier contact details are entered by you; we store and process them on your behalf.
          </li>
          <li>
            <strong>Payment information</strong>: this website doesn't take payments directly; we only record the activation codes you redeem. Screenshots, transaction IDs and messages uploaded for earlier QR-code payments are used only to match those payments. We never collect card numbers or payment passwords.
          </li>
          <li>
            <strong>Support chats</strong>: questions you ask the online assistant are processed by our AI provider, Anthropic, to write an answer from our help documents. Only the text of your question and the last few messages of the chat are sent, never your account information or store business data. AI answers and questions the assistant cannot answer are recorded to improve its answers, without your account or store information; AI answer records are deleted automatically after 90 days.
          </li>
          <li>
            <strong>Technical information</strong>: your IP address, used to prevent password guessing and abuse (for example limiting failed sign-ins).
          </li>
        </ul>
      </section>

      <section>
        <h2>2. Cookies</h2>
        <p>We use a single essential cookie to keep you signed in (for up to 30 days); scripts on the page cannot read it. We use no advertising cookies and no third-party analytics or tracking. Your language choice is remembered in your browser and on your account.</p>
      </section>

      <section>
        <h2>3. How we use and share information</h2>
        <ul>
          <li>Only to provide, maintain and improve the Service, and to handle payments and support requests.</li>
          <li>We do not sell your information or share it with third parties for marketing.</li>
          <li>The online assistant uses Anthropic's AI service to write answers; only the chat text is sent (see "Support chats" above).</li>
          <li>Data is stored on cloud servers we rent. We do not disclose your information to anyone else unless required by law.</li>
          <li>Platform administrators can see only store names, plan status and the owner's email and name (to handle payments and support), not stores' business data.</li>
        </ul>
      </section>

      <section>
        <h2>4. Retention and security</h2>
        <ul>
          <li>We keep your data while your account exists; expiry of a plan does not delete data. After you close your account we delete or anonymize your data within a reasonable period, unless the law requires us to keep it.</li>
          <li>Security measures include HTTPS everywhere, irreversible password hashing, isolation between stores, private payment screenshots and regular backups.</li>
        </ul>
      </section>

      <section>
        <h2>5. Your rights</h2>
        <p>You can view and edit your profile and store details in Settings. To access, export, correct or delete your personal information, or close your account, contact support and we will respond within 15 working days.</p>
      </section>

      <section>
        <h2>6. Minors</h2>
        <p>The Service is intended for business operators and not for anyone under 18.</p>
      </section>

      <section>
        <h2>7. Updates and contact</h2>
        <p>
          We will notify you on the website when this policy changes. If you have questions, use the help button in the bottom-right corner, or add our support WeChat{' '}
          <span className="font-mono">{SUPPORT_WECHAT}</span>.
        </p>
      </section>
    </>
  )
}
