/** Previous/next controls for a PageResponse from the API ({ page, totalPages, totalElements, first, last }). */
export default function Pager({ pageData, onPageChange, newerOlder = false }) {
  if (!pageData || pageData.totalPages <= 1) {
    return pageData ? <p className="muted small pager-summary">{pageData.totalElements} total</p> : null
  }
  return (
    <nav className="pager" aria-label="Pagination">
      <button
        type="button"
        className="button button--ghost"
        disabled={pageData.first}
        onClick={() => onPageChange(pageData.page - 1)}
      >
        {newerOlder ? '← Newer' : '← Previous'}
      </button>
      <span className="muted small">
        Page {pageData.page + 1} of {pageData.totalPages} · {pageData.totalElements} total
      </span>
      <button
        type="button"
        className="button button--ghost"
        disabled={pageData.last}
        onClick={() => onPageChange(pageData.page + 1)}
      >
        {newerOlder ? 'Older →' : 'Next →'}
      </button>
    </nav>
  )
}
