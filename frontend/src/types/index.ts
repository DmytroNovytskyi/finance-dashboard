/**
 * Types mirroring the backend REST DTOs (package com.financedashboard.web.dto).
 * Field names and JSON shapes must stay aligned with the OpenAPI contract.
 */

export type AccountKind = 'PERSONAL' | 'BUSINESS'
export type TransactionNature = 'INCOME' | 'EXPENSE' | 'TRANSFER'

/** Page-shaped response returned by list endpoints. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}

export interface Account {
  id: number
  name: string
  currency: string
  kind: AccountKind | null
  accountNumber: string | null
  sortOrder: number
}

export interface Category {
  id: number
  name: string
  color: string | null
  sortOrder: number
}

export interface Transaction {
  id: number
  accountId: number
  statementId: number
  transactionDate: string
  amount: number
  currency: string
  nature: TransactionNature
  description: string | null
  merchant: string | null
  categoryId: number | null
}

export interface Statement {
  id: number
  accountId: number
  bank: string
  periodStart: string | null
  periodEnd: string | null
  fileName: string | null
  importedAt: string
  transactionCount: number
}

export interface StatementImportResult {
  statementId: number | null
  alreadyImported: boolean
  imported: number
  skipped: number
}

export interface StatisticsSummary {
  from: string | null
  to: string | null
  baseCurrency: string
  totals: StatisticsTotals
  byMonth: StatisticsByMonth[]
  byCategory: StatisticsByCategory[]
  topMerchants: StatisticsByMerchant[]
  unconverted: number
}

export interface StatisticsTotals {
  income: number
  expense: number
  net: number
  count: number
  uncategorizedCount: number
  avgExpensePerDay: number
}

export interface StatisticsByMonth {
  /** ISO month, e.g. "2026-03". */
  month: string
  income: number
  expense: number
  net: number
}

export interface StatisticsByCategory {
  /** Null for the aggregated "(uncategorized)" bucket. */
  categoryId: number | null
  categoryName: string | null
  color: string | null
  income: number
  expense: number
  net: number
}

export interface StatisticsByMerchant {
  merchant: string
  count: number
  income: number
  expense: number
  net: number
}
