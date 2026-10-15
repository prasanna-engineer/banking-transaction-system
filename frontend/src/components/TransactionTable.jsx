import { formatDateTime, titleCase } from '../utils/format'
import { formatMoney } from '../utils/money'

/**
 * Shows transactions from the point of view of the logged-in customer:
 * money arriving in one of their accounts is a credit (+), money leaving is a debit (−),
 * and a move between two of their own accounts is "Internal".
 */
function direction(tx, myAccountIds) {
  // An admin owns no accounts, so there is no "my side" - show amounts without a sign.
  if (myAccountIds.size === 0) return 'neutral'
  const fromMine = tx.sourceAccountId != null && myAccountIds.has(tx.sourceAccountId)
  const toMine = tx.destinationAccountId != null && myAccountIds.has(tx.destinationAccountId)
  if (fromMine && toMine) return 'internal'
  if (fromMine) return 'debit'
  return 'credit'
}

const SIGN = { credit: '+', debit: '−', internal: '', neutral: '' }

export default function TransactionTable({ transactions, myAccountIds, caption }) {
  return (
    <div className="table-wrap">
      <table className="table">
        {caption && <caption className="visually-hidden">{caption}</caption>}
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Description</th>
            <th scope="col">From → To</th>
            <th scope="col">Status</th>
            <th scope="col" className="num">Amount</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((tx) => {
            const kind = direction(tx, myAccountIds)
            return (
              <tr key={tx.id}>
                <td>{formatDateTime(tx.createdAt)}</td>
                <td>
                  <div>{tx.description || titleCase(tx.type)}</div>
                  <div className="muted small">{tx.reference}</div>
                </td>
                <td className="muted">
                  {tx.maskedSourceAccountNumber ?? 'Deposit'} → {tx.maskedDestinationAccountNumber}
                </td>
                <td>
                  <span className="badge">{titleCase(tx.status)}</span>
                </td>
                <td className={`num amount amount--${kind}`}>
                  {kind !== 'neutral' && <span className="visually-hidden">{kind} </span>}
                  {SIGN[kind]}
                  {formatMoney(tx.amount, tx.currency)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
