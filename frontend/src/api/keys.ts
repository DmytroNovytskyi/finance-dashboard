import type { StatementListParams, StatisticsParams, TransactionListParams } from './endpoints'

/** TanStack Query key factories; invalidation targets the root of a group. */
export const queryKeys = {
  accounts: ['accounts'] as const,
  categories: ['categories'] as const,
  merchantRules: ['merchant-rules'] as const,
  statements: ['statements'] as const,
  statementList: (params: StatementListParams) => ['statements', 'list', params] as const,
  statementCoverage: ['statements', 'coverage'] as const,
  transfers: ['transfers'] as const,
  refunds: ['refunds'] as const,
  transactions: {
    root: ['transactions'] as const,
    list: (params: TransactionListParams) => ['transactions', 'list', params] as const,
  },
  statistics: {
    root: ['statistics'] as const,
    summary: (params: StatisticsParams) => ['statistics', 'summary', params] as const,
    categorySeries: (categoryId: number, params: StatisticsParams) =>
      ['statistics', 'category-series', categoryId, params] as const,
    categoryTrend: (categoryId: number, params: StatisticsParams) =>
      ['statistics', 'category-trend', categoryId, params] as const,
  },
}
