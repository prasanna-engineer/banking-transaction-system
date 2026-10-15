import { apiRequest } from './client'

/**
 * body: { sourceAccountId, destinationAccountId | destinationAccountNumber, amount: "12.50", description }
 * The amount stays a string all the way to the server, where it becomes a BigDecimal.
 *
 * idempotencyKey: one random key per transfer attempt. Sending the same request again with the same
 * key (e.g. retrying after a network error) returns the original result instead of moving money twice.
 *
 * otp: { challengeId, code } for transfers above the step-up threshold (see requestTransferOtp).
 */
export function createTransfer(body, idempotencyKey, otp) {
  const headers = { 'Idempotency-Key': idempotencyKey }
  if (otp) {
    headers['X-OTP-Challenge'] = otp.challengeId
    headers['X-OTP-Code'] = otp.code
  }
  return apiRequest('/transfers', { method: 'POST', body, headers })
}

/** Asks for a one-time code for exactly this transfer. DEMO: the code comes back in the response. */
export function requestTransferOtp(body) {
  return apiRequest('/transfers/otp', { method: 'POST', body })
}

/** A new random key, e.g. "3f2b8c1e-...". crypto.randomUUID needs https or localhost. */
export function newIdempotencyKey() {
  return crypto.randomUUID()
}
