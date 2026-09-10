/**
 * Types mirroring the backend REST DTOs (package com.financedashboard.web.dto).
 * Field names and JSON shapes must stay aligned with the OpenAPI contract.
 */

export type AccountKind = 'PERSONAL' | 'BUSINESS'
export type TransactionNature = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'REFUND'

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
  /** Reserved categories (e.g. "Transfer") cannot be deleted. */
  system: boolean
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

export interface PeriodGap {
  from: string
  to: string
}

export interface StatementCoverage {
  accountId: number
  earliestPeriodStart: string | null
  latestPeriodEnd: string | null
  statementCount: number
  gaps: PeriodGap[]
  missingPeriodEnds: string[]
}

export type StatisticsGranularity = 'day' | 'week' | 'month' | 'quarter' | 'year'

export interface StatisticsSummary {
  from: string | null
  to: string | null
  baseCurrency: string
  totals: StatisticsTotals
  byMonth: StatisticsByMonth[]
  byCategory: StatisticsByCategory[]
  topMerchants: StatisticsByMerchant[]
  trend: StatisticsTrendPoint[]
}

/** One category's income/expense per time bucket (used for the bucket granularities). */
export interface StatisticsCategoryTrend {
  baseCurrency: string
  trend: StatisticsTrendPoint[]
}

/** One category's individual transactions, each as a dated amount in the requested currency. */
export interface StatisticsCategorySeries {
  baseCurrency: string
  points: StatisticsCategorySeriesPoint[]
}

export interface StatisticsCategorySeriesPoint {
  /** ISO date of the transaction. */
  date: string
  /** Signed base-currency amount of that transaction (income positive, expense negative). */
  amount: number
}

export interface StatisticsTrendPoint {
  /** ISO start date of the time bucket, e.g. "2026-03-01". */
  start: string
  /** Inclusive ISO end date of the time bucket. */
  end: string
  income: number
  expense: number
  net: number
}

/** A user-managed merchant-to-category default. */
export interface MerchantRule {
  id: number
  merchant: string
  categoryId: number
  categoryName: string | null
  color: string | null
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

/** One detected internal transfer between the user's own accounts (not yet applied). */
export interface TransferSuggestion {
  fromTransactionId: number
  toTransactionId: number
  fromAccountId: number
  toAccountId: number
  amount: number
  currency: string
  fromDate: string
  toDate: string
  reason: 'MIRROR' | 'AMOUNT'
}

/** The two legs of an applied internal transfer. */
export interface TransferPair {
  transferGroupId: string | null
  from: Transaction
  to: Transaction
}

/** One detected purchase-and-refund pair (not yet linked). */
export interface RefundSuggestion {
  purchaseTransactionId: number
  refundTransactionId: number
  accountId: number
  amount: number
  currency: string
  /** The purchase's merchant, for display; null when the imported row carries none. */
  merchant: string | null
  purchaseDate: string
  refundDate: string
  /** ANCHORED when the bank's own wording named the purchase, AMOUNT when only the sum matched. */
  reason: 'ANCHORED' | 'AMOUNT'
}

/** The purchase and the refund of an applied pair. */
export interface RefundPair {
  refundGroupId: string | null
  purchase: Transaction
  refund: Transaction
}
