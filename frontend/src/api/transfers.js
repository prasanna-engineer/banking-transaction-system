import { apiRequest } from './client'

/**
 * body: { sourceAccountId, destinationAccountId | destinationAccountNumber, amount: "12.50", description }
 * The amount stays a string all the way to the server, where it becomes a BigDecimal.
 *
 * idempotencyKey: one random key per transfer attempt. Sending the same request again with the same
 * key (e.g. retrying after a network error) returns the original result instead of moving money twice.
 */
export function createTransfer(body, idempotencyKey) {
  return apiRequest('/transfers', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } })
}

/** A new random key, e.g. "3f2b8c1e-...". crypto.randomUUID needs https or localhost. */
export function newIdempotencyKey() {
  return crypto.randomUUID()
}
