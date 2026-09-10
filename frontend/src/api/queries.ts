import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import type { Account, Category } from '../types'
import { categoricalPalette, useScheme } from '../theme'
import { accountsApi, categoriesApi, refundsApi, statementsApi, transfersApi } from './endpoints'
import { queryKeys } from './keys'

/** All accounts, ordered as the backend returns them. */
export function useAccounts() {
  return useQuery({ queryKey: queryKeys.accounts, queryFn: accountsApi.list })
}

export interface CategoryPresentation {
  id: number
  name: string
  color: string
  system: boolean
}

/**
 * Categories in display order — alphabetical by name, with the reserved system category pinned
 * last so it never competes with the user's own groups — each with a resolved color.
 */
export function useCategories(): CategoryPresentation[] {
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: categoriesApi.list })
  const scheme = useScheme()
  return useMemo(() => orderCategories(categories.data ?? [], scheme), [categories.data, scheme])
}

function orderCategories(categories: Category[], scheme: 'light' | 'dark'): CategoryPresentation[] {
  const sorted = [...categories].sort(
    (a, b) => Number(a.system) - Number(b.system) || a.name.localeCompare(b.name),
  )
  return sorted.map((category, index) => ({
    id: category.id,
    name: category.name,
    color: category.color ?? categoricalPalette[scheme][index % categoricalPalette[scheme].length],
    system: category.system,
  }))
}

/** Own-account transfer pairs the matcher detected but are not yet internal transfers. */
export function useTransferSuggestions() {
  return useQuery({ queryKey: queryKeys.transfers, queryFn: transfersApi.suggestions })
}

/** Purchase-and-refund pairs the matcher detected but that are not linked yet. */
export function useRefundSuggestions() {
  return useQuery({ queryKey: queryKeys.refunds, queryFn: refundsApi.suggestions })
}

/** How far each account's imported statements reach, and where they are incomplete. */
export function useStatementCoverage() {
  return useQuery({ queryKey: queryKeys.statementCoverage, queryFn: statementsApi.coverage })
}

export interface AccountPresentation {
  id: number
  name: string
  currency: string
}

export function useAccountsById(): Map<number, AccountPresentation> {
  const accounts = useAccounts()
  return useMemo(() => {
    const map = new Map<number, AccountPresentation>()
    for (const account of (accounts.data ?? []) as Account[]) {
      map.set(account.id, { id: account.id, name: account.name, currency: account.currency })
    }
    return map
  }, [accounts.data])
}
