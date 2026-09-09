import type { StatisticsParams, TransactionListParams } from './endpoints'

/** TanStack Query key factories; invalidation targets the root of a group. */
export const queryKeys = {
  accounts: ['accounts'] as const,
  categories: ['categories'] as const,
  merchantRules: ['merchant-rules'] as const,
  statements: ['statements'] as const,
  transactions: {
    root: ['transactions'] as const,
    list: (params: TransactionListParams) => ['transactions', 'list', params] as const,
  },
  statistics: {
    root: ['statistics'] as const,
    summary: (params: StatisticsParams) => ['statistics', 'summary', params] as const,
  },
}
