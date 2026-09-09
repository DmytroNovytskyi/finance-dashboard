import type { TransactionListParams } from '../../api/endpoints'
import type { TransactionNature } from '../../types'

/** User-facing filters for the transactions list. */
export interface TxFilters {
  accountId?: number
  categoryId?: number
  uncategorized: boolean
  nature?: TransactionNature
  from?: string
  to?: string
  q: string
}

export const emptyFilters: TxFilters = { uncategorized: false, q: '' }

/** Initial filters read from the URL (used for overview drill-down links). */
export function filtersFromUrl(searchParams: URLSearchParams): TxFilters {
  const nature = searchParams.get('nature')
  const natureValue = nature === 'INCOME' || nature === 'EXPENSE' || nature === 'TRANSFER' ? nature : undefined
  const accountId = searchParams.get('accountId')
  const categoryId = searchParams.get('categoryId')
  const from = searchParams.get('from')
  const to = searchParams.get('to')
  return {
    accountId: accountId && /^\d+$/.test(accountId) ? Number(accountId) : undefined,
    categoryId: categoryId && /^\d+$/.test(categoryId) ? Number(categoryId) : undefined,
    uncategorized: searchParams.get('uncategorized') === 'true',
    nature: natureValue,
    from: from ?? undefined,
    to: to ?? undefined,
    q: '',
  }
}

/** Converts the filters into API list parameters (uncategorized and categoryId are exclusive). */
export function toListParams(filters: TxFilters, page: number, size: number): TransactionListParams {
  return {
    accountId: filters.accountId,
    categoryId: filters.uncategorized ? undefined : filters.categoryId,
    uncategorized: filters.uncategorized ? true : undefined,
    nature: filters.nature,
    from: filters.from || undefined,
    to: filters.to || undefined,
    q: filters.q || undefined,
    page,
    size,
  }
}

export function hasFilters(filters: TxFilters): boolean {
  return filters.accountId !== undefined || filters.categoryId !== undefined || filters.uncategorized ||
    filters.nature !== undefined || filters.from !== undefined || filters.to !== undefined || filters.q !== ''
}
