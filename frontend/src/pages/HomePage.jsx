import { Link } from 'react-router-dom'
import {
  ArrowRightIcon,
  ChartIcon,
  ClockIcon,
  ListIcon,
  LockIcon,
  ShieldIcon,
  TransferIcon,
  UsersIcon,
  WalletIcon,
} from '../components/Icons'
import PublicHeader from '../components/PublicHeader'
import SiteFooter from '../components/SiteFooter'
import { useAuth } from '../features/auth/AuthContext'

// Honeycomb rows for the hero banner: purely decorative.
const HEX_ROWS = [
  [UsersIcon, null, ChartIcon],
  [TransferIcon, ShieldIcon, null, ClockIcon],
  [null, WalletIcon, LockIcon],
]

const SERVICES = [
  {
    icon: TransferIcon,
    title: 'Instant transfers',
    text: 'Move money between your accounts or to another customer in seconds — with a review step before you confirm.',
    to: '/transfer',
    link: 'Make a transfer',
  },
  {
    icon: ListIcon,
    title: 'Transaction history',
    text: 'Search every payment by account, date, type and status. Filters stay in the address bar, so you can bookmark them.',
    to: '/transactions',
    link: 'View history',
  },
  {
    icon: WalletIcon,
    title: 'All your accounts',
    text: 'Savings and current accounts side by side, with balances that are always calculated by the bank, never the browser.',
    to: '/accounts',
    link: 'See accounts',
  },
  {
    icon: ShieldIcon,
    title: 'Safe by design',
    text: 'Every transfer is all-or-nothing, protected against double submission, and written to an audit log.',
    to: '/#security',
    link: 'How we protect you',
  },
]

const SECURITY_POINTS = [
  { icon: LockIcon, title: 'Encrypted passwords', text: 'Passwords are stored only as BCrypt hashes and never logged.' },
  { icon: ShieldIcon, title: 'Atomic transfers', text: 'Debit and credit happen together or not at all — even under heavy load.' },
  { icon: ClockIcon, title: 'Safe retries', text: 'A repeated request returns the original result instead of paying twice.' },
]

export default function HomePage() {
  const { status } = useAuth()
  const signedIn = status === 'authenticated'

  return (
    <div className="public-page">
      <PublicHeader />

      <main id="main">
        <section className="hero" aria-labelledby="hero-title">
          <div className="hero__text">
            <h1 id="hero-title">Banking that&apos;s simple, safe and instant</h1>
            <p className="hero__lead">
              Check balances, send money and track every rupee in one place — a demo banking experience built
              with Java, Spring Boot and React.
            </p>
            <div className="button-row">
              <Link to={signedIn ? '/dashboard' : '/register'} className="button button--primary button--large">
                {signedIn ? 'Go to dashboard' : 'Get started'}
              </Link>
              {!signedIn && (
                <Link to="/login" className="button button--large">
                  Sign in
                </Link>
              )}
            </div>
            <p className="muted small hero__note">Fictional data only · no real money</p>
          </div>

          <div className="hero__art" aria-hidden="true">
            {HEX_ROWS.map((row, r) => (
              <div className={`hex-row ${r % 2 === 1 ? 'hex-row--offset' : ''}`} key={r}>
                {row.map((HexIcon, c) => (
                  <div className={`hex hex--${(r + c) % 3}`} key={c}>
                    {HexIcon && <HexIcon size={40} />}
                  </div>
                ))}
              </div>
            ))}
          </div>
        </section>

        <section id="services" className="home-section" aria-labelledby="services-title">
          <h2 id="services-title" className="home-section__title">
            Featured services
          </h2>
          <div className="service-grid">
            {SERVICES.map(({ icon: ServiceIcon, title, text, to, link }) => (
              <article className="service-card" key={title}>
                <div className="service-card__band">
                  <span className="service-card__icon">
                    <ServiceIcon size={34} />
                  </span>
                </div>
                <div className="service-card__body">
                  <h3>{title}</h3>
                  <p className="muted">{text}</p>
                  <Link to={to} className="service-card__link">
                    {link} <ArrowRightIcon />
                  </Link>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section id="security" className="home-section security-band" aria-labelledby="security-title">
          <h2 id="security-title" className="home-section__title">
            Built to keep your money safe
          </h2>
          <ul className="security-list">
            {SECURITY_POINTS.map(({ icon: PointIcon, title, text }) => (
              <li key={title}>
                <span className="security-list__icon">
                  <PointIcon size={26} />
                </span>
                <div>
                  <h3>{title}</h3>
                  <p className="muted">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <SiteFooter />
    </div>
  )
}
