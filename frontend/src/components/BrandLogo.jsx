/** DemoBank wordmark: a hexagon mark plus the name. A fictional brand - not any real bank's logo. */
export default function BrandLogo() {
  return (
    <span className="brand">
      <svg className="brand__mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
        <path d="M16 2l12 7v14l-12 7-12-7V9z" fill="#fff" />
        <path d="M11 11h6.5a3.5 3.5 0 010 7H11zM11 18h7.5a3.5 3.5 0 010 7H11z" fill="#C2410C" />
      </svg>
      <span className="brand__name">
        Demo<strong>Bank</strong>
      </span>
    </span>
  )
}
