import { useCallback, useEffect, useState } from 'react'

/**
 * Loads data when the component mounts (and when deps change), tracking loading / success / error.
 * Cancels the request if the component unmounts first, so a slow response never updates a closed page.
 *
 *   const { status, data, error, reload } = useApi((signal) => listAccounts(signal), [])
 */
export function useApi(fetcher, deps) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setState((previous) => ({ ...previous, status: 'loading', error: null }))
    fetcher(controller.signal)
      .then((data) => setState({ status: 'success', data, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') {
          setState({ status: 'error', data: null, error })
        }
      })
    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadKey])

  const reload = useCallback(() => setReloadKey((key) => key + 1), [])
  return { ...state, reload }
}
