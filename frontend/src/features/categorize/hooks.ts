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

/** Uncategorized transactions of one nature (transfers are excluded by the nature filter). */
export function useUncategorizedQueue(nature: 'EXPENSE' | 'INCOME') {
  return useQuery({
    queryKey: queryKeys.transactions.list({ uncategorized: true, nature, page: 0, size: 200 }),
    queryFn: () => transactionsApi.list({ uncategorized: true, nature, page: 0, size: 200 }),
  })
}

/** Assigns a single transaction to a category (null clears it). */
export function useCategorizeOne() {
  const invalidate = useInvalidate(queryKeys.transactions.root, queryKeys.statistics.root)
  return useMutation({
    mutationFn: ({ id, categoryId }: { id: number; categoryId: number | null }) =>
      transactionsApi.update(id, { categoryId }),
    onSuccess: invalidate,
  })
}

/** Assigns a category to many transactions at once. */
export function useCategorizeBulk() {
  const invalidate = useInvalidate(queryKeys.transactions.root, queryKeys.statistics.root)
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
  const invalidate = useInvalidate(queryKeys.merchantRules)
  return useMutation({
    mutationFn: (id: number) => merchantRulesApi.remove(id),
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
