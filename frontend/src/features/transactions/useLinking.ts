import { useMutation, useQueryClient } from '@tanstack/react-query'
import { refundsApi, transfersApi } from '../../api/endpoints'
import { invalidatePairs } from './invalidatePairs'

interface LinkingOptions {
  /** Receives the message a link produced, or the reason the server refused it. */
  onNotice: (message: string) => void
  /** Called once a link is stored, so the caller can drop the selection that has just been paired. */
  onLinked: () => void
}

/**
 * Pairing a selection as a refund or as an internal transfer. Both legs leave the statistics and
 * take a reserved category, so every list that can show them has to be told: the two pairing flows
 * live here together so each caller invalidates the same keys and reports the same outcome.
 */
export function useLinking({ onNotice, onLinked }: LinkingOptions) {
  const queryClient = useQueryClient()

  const invalidate = () => invalidatePairs(queryClient)

  const linkRefund = useMutation({
    mutationFn: (ids: number[]) => refundsApi.pair(ids),
    onSuccess: (result) => {
      invalidate()
      onLinked()
      onNotice(`Linked ${result.transactions.length} transactions as a refund.`)
    },
    onError: (error: Error) => onNotice(error.message),
  })

  const linkTransfer = useMutation({
    mutationFn: ({ fromId, toId }: { fromId: number; toId: number }) => transfersApi.pair(fromId, toId),
    onSuccess: () => {
      invalidate()
      onLinked()
      onNotice('Linked the two transactions as an internal transfer.')
    },
    onError: (error: Error) => onNotice(error.message),
  })

  return {
    linkRefund: (ids: number[]) => linkRefund.mutate(ids),
    linkTransfer: (fromId: number, toId: number) => linkTransfer.mutate({ fromId, toId }),
    busy: linkRefund.isPending || linkTransfer.isPending,
  }
}
