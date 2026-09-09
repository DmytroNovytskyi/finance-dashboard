import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import type { Account, Category } from '../types'
import { categoricalPalette, useScheme } from '../theme'
import { accountsApi, categoriesApi } from './endpoints'
import { queryKeys } from './keys'

/** All accounts, ordered as the backend returns them. */
export function useAccounts() {
  return useQuery({ queryKey: queryKeys.accounts, queryFn: accountsApi.list })
}

export interface CategoryPresentation {
  id: number
  name: string
  color: string
}

/** Categories in display order (sortOrder, then name), each with a resolved color. */
export function useCategories(): CategoryPresentation[] {
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: categoriesApi.list })
  const scheme = useScheme()
  return useMemo(() => orderCategories(categories.data ?? [], scheme), [categories.data, scheme])
}

function orderCategories(categories: Category[], scheme: 'light' | 'dark'): CategoryPresentation[] {
  const sorted = [...categories].sort(
    (a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name),
  )
  return sorted.map((category, index) => ({
    id: category.id,
    name: category.name,
    color: category.color ?? categoricalPalette[scheme][index % categoricalPalette[scheme].length],
  }))
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
