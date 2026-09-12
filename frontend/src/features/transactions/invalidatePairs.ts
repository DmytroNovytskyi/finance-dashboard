import type { QueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../api/keys'

/**
 * Refreshes every list a pairing change can affect. Linking, unlinking and applying suggestions all
 * move rows between the same views, so they share this rather than each naming the keys and drifting
 * apart — a leg that changes nature leaves the statistics and the pending suggestions at once.
 */
export function invalidatePairs(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: queryKeys.transfers })
  queryClient.invalidateQueries({ queryKey: queryKeys.refunds })
  queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
  queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
}
