import type { TransactionListParams, TransactionSortKey } from '../../api/endpoints'
import { modeForRange, type DateMode } from '../../lib/date'
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
  /**
   * Whether the dates above are being written as one calendar month or as a day range. It is a
   * view of the same dates rather than a filter of its own, so it is left out of {@link hasFilters}.
   */
  dateMode: DateMode
  q: string
  /** An explicit set of rows, set by drilling into a suggested pair; empty means no constraint. */
  ids?: number[]
  /** One merchant, set by drilling into the merchant chart; undefined means no constraint. */
  merchant?: string
  /** Set by drilling into the merchants the statistics could not attribute; selects rows with none. */
  withoutMerchant?: boolean
}

export const emptyFilters: TxFilters = { uncategorized: false, q: '', dateMode: 'month' }

/**
 * The natures a filter may carry, which are the ones the filter bar offers. Linked transfers and
 * refunds are reached through the Category filter instead, so accepting their natures here would
 * let a hand-written link filter the list by a value the bar cannot show or clear.
 */
const NATURES: TransactionNature[] = ['INCOME', 'EXPENSE']

/** Initial filters read from the URL (used for overview and suggestion drill-down links). */
export function filtersFromUrl(searchParams: URLSearchParams): TxFilters {
  const nature = searchParams.get('nature') as TransactionNature | null
  const natureValue = nature && NATURES.includes(nature) ? nature : undefined
  const accountId = searchParams.get('accountId')
  const categoryId = searchParams.get('categoryId')
  const from = searchParams.get('from') ?? null
  const to = searchParams.get('to') ?? null
  const ids = searchParams.getAll('ids').filter((id) => /^\d+$/.test(id)).map(Number)
  const merchant = searchParams.get('merchant')
  return {
    accountId: accountId && /^\d+$/.test(accountId) ? Number(accountId) : undefined,
    categoryId: categoryId && /^\d+$/.test(categoryId) ? Number(categoryId) : undefined,
    uncategorized: searchParams.get('uncategorized') === 'true',
    nature: natureValue,
    from: from ?? undefined,
    to: to ?? undefined,
    dateMode: modeForRange({ from, to }),
    q: '',
    ids: ids.length > 0 ? ids : undefined,
    merchant: merchant === null ? undefined : merchant,
    withoutMerchant: searchParams.get('withoutMerchant') === 'true' ? true : undefined,
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
    merchant: filters.merchant,
    withoutMerchant: filters.withoutMerchant ? true : undefined,
    ...(sort ? { sort: sort.key, order: sort.dir } : {}),
    page,
    size,
  }
}

export function hasFilters(filters: TxFilters): boolean {
  return filters.accountId !== undefined || filters.categoryId !== undefined || filters.uncategorized ||
    filters.nature !== undefined || filters.from !== undefined || filters.to !== undefined ||
    filters.q !== '' || filters.ids !== undefined || filters.merchant !== undefined ||
    filters.withoutMerchant === true
}
