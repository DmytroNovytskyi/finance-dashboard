import type {
  Account,
  Category,
  MerchantRule,
  PageResponse,
  Statement,
  StatementImportResult,
  StatisticsGranularity,
  StatisticsSummary,
  Transaction,
  TransactionNature,
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
  page?: number
  size?: number
}

export interface StatisticsParams {
  from?: string
  to?: string
  accountId?: number
  kind?: string
  topN?: number
  granularity?: StatisticsGranularity
}

export interface CategoryInput {
  name: string
  color?: string | null
}

export const accountsApi = {
  list: () => request<Account[]>('/accounts'),
}

export const categoriesApi = {
  list: () => request<Category[]>('/categories'),
  create: (input: CategoryInput) =>
    request<Category>('/categories', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: number, input: { name?: string; color?: string | null }) =>
    request<Category>(`/categories/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
  remove: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }),
}

export const statementsApi = {
  list: () => request<Statement[]>('/statements'),
  importPdf: (accountId: number, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<StatementImportResult>(`/statements?accountId=${accountId}`, {
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
  deleteRange: (from: string, to: string, accountId?: number) =>
    request<void>(`/transactions${buildQuery({ from, to, accountId })}`, { method: 'DELETE' }),
}

export const statisticsApi = {
  summary: (params: StatisticsParams) =>
    request<StatisticsSummary>(`/statistics/summary${buildQuery(params)}`),
}

export const merchantRulesApi = {
  list: () => request<MerchantRule[]>('/merchant-rules'),
  create: (input: { merchant: string; categoryId: number }) =>
    request<MerchantRule>('/merchant-rules', { method: 'POST', body: JSON.stringify(input) }),
  remove: (id: number) => request<void>(`/merchant-rules/${id}`, { method: 'DELETE' }),
  apply: () => request<{ applied: number }>('/merchant-rules/apply', { method: 'POST' }),
}
