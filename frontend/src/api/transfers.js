import { apiRequest } from './client'

/**
 * body: { sourceAccountId, destinationAccountId | destinationAccountNumber, amount: "12.50", description }
 * The amount stays a string all the way to the server, where it becomes a BigDecimal.
 */
export function createTransfer(body) {
  return apiRequest('/transfers', { method: 'POST', body })
}
