import type {
  Account,
  Category,
  MerchantRule,
  PageResponse,
  RefundPair,
  RefundSuggestion,
  Statement,
  StatementCoverage,
  StatementImportResult,
  StatisticsCategorySeries,
  StatisticsCategoryTrend,
  StatisticsGranularity,
  StatisticsSummary,
  Transaction,
  TransactionNature,
  TransferPair,
  TransferSuggestion,
} from '../types'
import { buildQuery, request } from './client'

export interface TransactionListParams {
  accountId?: number
  categoryId?: number
  uncategorized?: boolean
  nature?: TransactionNature
  from?: string
  to?: string
  q?: string
  sort?: TransactionSortKey
  order?: 'asc' | 'desc'
  page?: number
  size?: number
}

export type TransactionSortKey = 'date' | 'amount' | 'account' | 'category'

export type StatementSortKey = 'imported' | 'file' | 'account' | 'period'

export interface StatisticsParams {
  from?: string
  to?: string
  accountId?: number
  kind?: string
  topN?: number
  granularity?: StatisticsGranularity
  /** Selects which stored per-transaction currency is summed for the figures. */
  displayCurrency?: string
}

export interface CategoryInput {
  name: string
  color?: string | null
}

export interface AccountInput {
  name: string
  currency: string
  kind?: Account['kind']
  accountNumber?: string | null
}

/** The user-owned account fields; the server owns the currency and the account number. */
export interface AccountUpdateInput {
  name: string
  kind?: Account['kind'] | null
}

export const accountsApi = {
  list: () => request<Account[]>('/accounts'),
  create: (input: AccountInput) =>
    request<Account>('/accounts', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: number, input: AccountUpdateInput) =>
    request<Account>(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
  /** Removes the account together with all its statements and transactions. */
  remove: (id: number) => request<{ count: number }>(`/accounts/${id}`, { method: 'DELETE' }),
}

export const categoriesApi = {
  list: () => request<Category[]>('/categories'),
  create: (input: CategoryInput) =>
    request<Category>('/categories', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: number, input: { name?: string; color?: string | null }) =>
    request<Category>(`/categories/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
  remove: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }),
  /** Clears the category from its transactions, keeping the category itself. */
  uncategorize: (id: number) =>
    request<{ count: number }>(`/categories/${id}/uncategorize`, { method: 'POST' }),
}

/** Server-side ordering and filtering of the statements list. */
export interface StatementListParams {
  sort?: StatementSortKey
  order?: 'asc' | 'desc'
  accountId?: number
}

export const statementsApi = {
  list: (params: StatementListParams = {}) =>
    request<Statement[]>(`/statements${buildQuery(params)}`),
  coverage: () => request<StatementCoverage[]>('/statements/coverage'),
  importPdf: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<StatementImportResult>('/statements', {
      method: 'POST',
      body,
    })
  },
  remove: (id: number) => request<void>(`/statements/${id}`, { method: 'DELETE' }),
}

export const transactionsApi = {
  list: (params: TransactionListParams) =>
    request<PageResponse<Transaction>>(`/transactions${buildQuery(params)}`),
  /** Sets category and/or nature of one transaction; categoryId null clears the category. */
  update: (id: number, body: { categoryId?: number | null; nature?: TransactionNature }) =>
    request<Transaction>(`/transactions/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  categorizeBulk: (transactionIds: number[], categoryId: number | null) =>
    request<void>('/transactions/categorize', {
      method: 'POST',
      body: JSON.stringify({ transactionIds, categoryId }),
    }),
  uncategorizeAll: () =>
    request<{ count: number }>('/transactions/uncategorize-all', { method: 'POST' }),
  deleteRange: (from: string, to: string, accountId?: number) =>
    request<void>(`/transactions${buildQuery({ from, to, accountId })}`, { method: 'DELETE' }),
}

export const statisticsApi = {
  summary: (params: StatisticsParams) =>
    request<StatisticsSummary>(`/statistics/summary${buildQuery(params)}`),
  /** One category's individual transactions as dated base-currency amounts. */
  categorySeries: (categoryId: number, params: StatisticsParams) =>
    request<StatisticsCategorySeries>(`/statistics/categories/${categoryId}/transactions${buildQuery(params)}`),
  /** One category's income/expense bucketed by granularity. */
  categoryTrend: (categoryId: number, params: StatisticsParams) =>
    request<StatisticsCategoryTrend>(`/statistics/categories/${categoryId}/trend${buildQuery(params)}`),
}

export const transfersApi = {
  /** Detected own-account transfer pairs that are not yet internal transfers. */
  suggestions: () => request<TransferSuggestion[]>('/transfers/suggestions'),
  /** Applies one pair as an internal transfer. */
  pair: (fromTransactionId: number, toTransactionId: number) =>
    request<TransferPair>('/transfers', {
      method: 'POST',
      body: JSON.stringify({ fromTransactionId, toTransactionId }),
    }),
  /** Applies every current suggestion as an internal transfer. */
  applyAll: () => request<{ applied: number }>('/transfers/suggestions/apply', { method: 'POST' }),
  /** Reverts an internal transfer back to its natural income/expense legs. */
  unlink: (transactionId: number) =>
    request<{ count: number }>(`/transfers/${transactionId}/unlink`, { method: 'POST' }),
}

export const refundsApi = {
  /** Detected purchase-and-refund pairs that are not linked yet. */
  suggestions: () => request<RefundSuggestion[]>('/refunds/suggestions'),
  /** Links one purchase with its refund. */
  pair: (purchaseTransactionId: number, refundTransactionId: number) =>
    request<RefundPair>('/refunds', {
      method: 'POST',
      body: JSON.stringify({ purchaseTransactionId, refundTransactionId }),
    }),
  /** Links every current suggestion. */
  applyAll: () => request<{ applied: number }>('/refunds/suggestions/apply', { method: 'POST' }),
  /** Reverts a linked pair back to its natural income/expense legs. */
  unlink: (transactionId: number) =>
    request<{ count: number }>(`/refunds/${transactionId}/unlink`, { method: 'POST' }),
}

export const merchantRulesApi = {
  list: () => request<MerchantRule[]>('/merchant-rules'),
  create: (input: { merchant: string; categoryId: number }) =>
    request<MerchantRule>('/merchant-rules', { method: 'POST', body: JSON.stringify(input) }),
  remove: (id: number) => request<void>(`/merchant-rules/${id}`, { method: 'DELETE' }),
  unlink: (id: number) => request<{ count: number }>(`/merchant-rules/${id}/unlink`, { method: 'POST' }),
  clearAll: () =>
    request<{ rulesRemoved: number; transactionsUncategorized: number }>('/merchant-rules', { method: 'DELETE' }),
  apply: () => request<{ applied: number }>('/merchant-rules/apply', { method: 'POST' }),
  applyOne: (id: number) => request<{ applied: number }>(`/merchant-rules/${id}/apply`, { method: 'POST' }),
}
