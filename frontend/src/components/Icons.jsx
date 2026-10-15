// Small line icons drawn with SVG so the app needs no image files or icon library.
// They are decorative (aria-hidden); the text next to them carries the meaning.

function Icon({ children, size = 28 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  )
}

export const TransferIcon = (props) => (
  <Icon {...props}>
    <path d="M4 8h13l-3-3M20 16H7l3 3" />
  </Icon>
)

export const ShieldIcon = (props) => (
  <Icon {...props}>
    <path d="M12 3l7 3v5c0 4.5-3 8.2-7 10-4-1.8-7-5.5-7-10V6l7-3z" />
    <path d="M9 12l2 2 4-4" />
  </Icon>
)

export const ChartIcon = (props) => (
  <Icon {...props}>
    <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
  </Icon>
)

export const ClockIcon = (props) => (
  <Icon {...props}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </Icon>
)

export const UsersIcon = (props) => (
  <Icon {...props}>
    <circle cx="9" cy="8" r="3" />
    <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 4.5a3 3 0 010 6M21 20c0-2.6-1.6-4.8-4-5.6" />
  </Icon>
)

export const LockIcon = (props) => (
  <Icon {...props}>
    <rect x="5" y="11" width="14" height="10" rx="2" />
    <path d="M8 11V8a4 4 0 018 0v3" />
  </Icon>
)

export const WalletIcon = (props) => (
  <Icon {...props}>
    <path d="M3 7h16a2 2 0 012 2v9a2 2 0 01-2 2H3V7z" />
    <path d="M3 7l12-3v3M17 13h2" />
  </Icon>
)

export const ListIcon = (props) => (
  <Icon {...props}>
    <path d="M9 6h12M9 12h12M9 18h12M4 6h.01M4 12h.01M4 18h.01" />
  </Icon>
)

export const ArrowRightIcon = (props) => (
  <Icon size={18} {...props}>
    <path d="M5 12h14M13 6l6 6-6 6" />
  </Icon>
)
