import { useCallback, useEffect, useState } from 'react'

/**
 * Loads data when the component mounts (and when deps change), tracking loading / success / error.
 * Cancels the request if the component unmounts first, so a slow response never updates a closed page.
 *
 * When data is already on screen, a reload keeps showing it (status stays 'success', refreshing = true)
 * instead of flashing a spinner - important for live updates that refresh pages in the background.
 *
 *   const { status, data, error, refreshing, reload } = useApi((signal) => listAccounts(signal), [])
 */
export function useApi(fetcher, deps) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null, refreshing: false })
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setState((previous) =>
      previous.data !== null
        ? { ...previous, refreshing: true, error: null }
        : { status: 'loading', data: null, error: null, refreshing: false },
    )
    fetcher(controller.signal)
      .then((data) => setState({ status: 'success', data, error: null, refreshing: false }))
      .catch((error) => {
        if (error.name !== 'AbortError') {
          setState({ status: 'error', data: null, error, refreshing: false })
        }
      })
    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadKey])

  const reload = useCallback(() => setReloadKey((key) => key + 1), [])
  return { ...state, reload }
}
