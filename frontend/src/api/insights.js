import { apiRequest } from './client'

export function getMonthlyFlow({ months = 6, currency = 'INR' } = {}, signal) {
  return apiRequest(`/insights/monthly-flow?months=${months}&currency=${encodeURIComponent(currency)}`, { signal })
}
