import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { categoriesApi, merchantRulesApi, transactionsApi } from '../../api/endpoints'
import { queryKeys } from '../../api/keys'

function useInvalidate(...keys: ReadonlyArray<readonly string[]>) {
  const queryClient = useQueryClient()
  return () => {
    for (const key of keys) {
      queryClient.invalidateQueries({ queryKey: key })
    }
  }
}

/** Rows the queue fetches in one go; it pages them in the browser. */
export const QUEUE_SIZE = 200

/** Which rows the uncategorized queue shows; ALL leaves the nature unfiltered. */
export type QueueNature = 'EXPENSE' | 'INCOME' | 'ALL'

/**
 * Uncategorized transactions. Transfers never appear: they always carry the reserved Internal
 * Transfer category, so the uncategorized filter excludes them however the nature is set.
 */
export function useUncategorizedQueue(nature: QueueNature) {
  const params = {
    uncategorized: true,
    nature: nature === 'ALL' ? undefined : nature,
    page: 0,
    size: QUEUE_SIZE,
  }
  return useQuery({
    queryKey: queryKeys.transactions.list(params),
    queryFn: () => transactionsApi.list(params),
  })
}

/** Assigns a single transaction to a category (null clears it). */
export function useCategorizeOne() {
  const invalidate = useInvalidate(
    queryKeys.transactions.root,
    queryKeys.statistics.root,
    queryKeys.transfers,
    queryKeys.refunds,
  )
  return useMutation({
    mutationFn: ({ id, categoryId }: { id: number; categoryId: number | null }) =>
      transactionsApi.update(id, { categoryId }),
    onSuccess: invalidate,
  })
}

/** Assigns a category to many transactions at once. */
export function useCategorizeBulk() {
  const invalidate = useInvalidate(
    queryKeys.transactions.root,
    queryKeys.statistics.root,
    queryKeys.transfers,
    queryKeys.refunds,
  )
  return useMutation({
    mutationFn: ({ ids, categoryId }: { ids: number[]; categoryId: number | null }) =>
      transactionsApi.categorizeBulk(ids, categoryId),
    onSuccess: invalidate,
  })
}

export function useCreateCategory() {
  const invalidate = useInvalidate(queryKeys.categories, queryKeys.statistics.root)
  return useMutation({
    mutationFn: (input: { name: string; color?: string }) => categoriesApi.create(input),
    onSuccess: invalidate,
  })
}

export function useUpdateCategory() {
  const invalidate = useInvalidate(queryKeys.categories, queryKeys.statistics.root)
  return useMutation({
    mutationFn: ({ id, name, color }: { id: number; name?: string; color?: string }) =>
      categoriesApi.update(id, { name, color }),
    onSuccess: invalidate,
  })
}

export function useDeleteCategory() {
  const invalidate = useInvalidate(queryKeys.categories, queryKeys.statistics.root, queryKeys.transactions.root)
  return useMutation({
    mutationFn: (id: number) => categoriesApi.remove(id),
    onSuccess: invalidate,
  })
}

/** Clears the category from its transactions, keeping the category itself. */
export function useUncategorizeCategory() {
  const invalidate = useInvalidate(queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: (id: number) => categoriesApi.uncategorize(id),
    onSuccess: invalidate,
  })
}

/** Merchant-to-category defaults. */
export function useMerchantRules() {
  return useQuery({ queryKey: queryKeys.merchantRules, queryFn: merchantRulesApi.list })
}

export function useCreateMerchantRule() {
  const invalidate = useInvalidate(queryKeys.merchantRules)
  return useMutation({
    mutationFn: ({ merchant, categoryId }: { merchant: string; categoryId: number }) =>
      merchantRulesApi.create({ merchant, categoryId }),
    onSuccess: invalidate,
  })
}

export function useDeleteMerchantRule() {
  const invalidate = useInvalidate(queryKeys.merchantRules, queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: (id: number) => merchantRulesApi.remove(id),
    onSuccess: invalidate,
  })
}

/** Clears a default from the rows it tagged while keeping the default itself. */
export function useUnlinkMerchantRule() {
  const invalidate = useInvalidate(queryKeys.merchantRules, queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: (id: number) => merchantRulesApi.unlink(id),
    onSuccess: invalidate,
  })
}

/** Applies one default to the uncategorized rows that match its counterparty. */
export function useApplyMerchantRule() {
  const invalidate = useInvalidate(queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: (id: number) => merchantRulesApi.applyOne(id),
    onSuccess: invalidate,
  })
}

/** Applies all defaults to the uncategorized history. */
export function useApplyMerchantRules() {
  const invalidate = useInvalidate(queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: () => merchantRulesApi.apply(),
    onSuccess: invalidate,
  })
}

/** Removes every default and reverts the transactions it had auto-tagged. */
export function useClearMerchantRules() {
  const invalidate = useInvalidate(queryKeys.merchantRules, queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: () => merchantRulesApi.clearAll(),
    onSuccess: invalidate,
  })
}

/** Clears the category on every categorized transaction (except internal transfers). */
export function useUncategorizeAll() {
  const invalidate = useInvalidate(queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: () => transactionsApi.uncategorizeAll(),
    onSuccess: invalidate,
  })
}
