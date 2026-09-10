import type { TransactionListParams, TransactionSortKey } from '../../api/endpoints'
import type { TransactionNature } from '../../types'

/** Current sort of the transactions list. */
export interface TransactionSort {
  key: TransactionSortKey
  dir: 'asc' | 'desc'
}

export const DEFAULT_TRANSACTION_SORT: TransactionSort = { key: 'date', dir: 'desc' }

/** User-facing filters for the transactions list. */
export interface TxFilters {
  accountId?: number
  categoryId?: number
  uncategorized: boolean
  nature?: TransactionNature
  from?: string
  to?: string
  q: string
  /** An explicit set of rows, set by drilling into a suggested pair; empty means no constraint. */
  ids?: number[]
}

export const emptyFilters: TxFilters = { uncategorized: false, q: '' }

const NATURES: TransactionNature[] = ['INCOME', 'EXPENSE', 'TRANSFER', 'REFUND']

/** Initial filters read from the URL (used for overview and suggestion drill-down links). */
export function filtersFromUrl(searchParams: URLSearchParams): TxFilters {
  const nature = searchParams.get('nature') as TransactionNature | null
  const natureValue = nature && NATURES.includes(nature) ? nature : undefined
  const accountId = searchParams.get('accountId')
  const categoryId = searchParams.get('categoryId')
  const from = searchParams.get('from')
  const to = searchParams.get('to')
  const ids = searchParams.getAll('ids').filter((id) => /^\d+$/.test(id)).map(Number)
  return {
    accountId: accountId && /^\d+$/.test(accountId) ? Number(accountId) : undefined,
    categoryId: categoryId && /^\d+$/.test(categoryId) ? Number(categoryId) : undefined,
    uncategorized: searchParams.get('uncategorized') === 'true',
    nature: natureValue,
    from: from ?? undefined,
    to: to ?? undefined,
    q: '',
    ids: ids.length > 0 ? ids : undefined,
  }
}

/** Converts the filters into API list parameters (uncategorized and categoryId are exclusive). */
/**
 * The words that stand for the pair tags rather than for text to match. Typing one selects the rows
 * carrying that tag, which is knowledge the list only has because it fetched the suggestions; the
 * tag itself is not stored on the transaction.
 */
const TAG_WORDS: Record<string, 'refund' | 'internal'> = {
  refund: 'refund',
  refunds: 'refund',
  internal: 'internal',
  transfer: 'internal',
  transfers: 'internal',
}

/** Which tag a search box value stands for, or null when it is ordinary search text. */
export function tagWordOf(value: string): 'refund' | 'internal' | null {
  return TAG_WORDS[value.trim().toLowerCase()] ?? null
}

export function toListParams(
  filters: TxFilters,
  page: number,
  size: number,
  sort?: TransactionSort,
): TransactionListParams {
  return {
    accountId: filters.accountId,
    categoryId: filters.uncategorized ? undefined : filters.categoryId,
    uncategorized: filters.uncategorized ? true : undefined,
    nature: filters.nature,
    from: filters.from || undefined,
    to: filters.to || undefined,
    q: filters.ids && filters.ids.length > 0 ? undefined : filters.q || undefined,
    ids: filters.ids && filters.ids.length > 0 ? filters.ids : undefined,
    ...(sort ? { sort: sort.key, order: sort.dir } : {}),
    page,
    size,
  }
}

export function hasFilters(filters: TxFilters): boolean {
  return filters.accountId !== undefined || filters.categoryId !== undefined || filters.uncategorized ||
    filters.nature !== undefined || filters.from !== undefined || filters.to !== undefined ||
    filters.q !== '' || filters.ids !== undefined
}
