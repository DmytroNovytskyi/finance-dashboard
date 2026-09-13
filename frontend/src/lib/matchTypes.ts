import type { MatchType } from '../types'

/**
 * The match types a default can use, narrowest first — the same order the backend resolves ties in,
 * so the list reads the way precedence works. The words are the ones shown in the picker and beside
 * a saved default.
 */
export const MATCH_TYPE_OPTIONS: { value: MatchType; label: string }[] = [
  { value: 'EQUALS', label: 'Equals' },
  { value: 'STARTS_WITH', label: 'Starts with' },
  { value: 'CONTAINS', label: 'Contains' },
]

/** The label of one match type, for the places that name it rather than offer it. */
export function matchTypeLabel(value: MatchType): string {
  return MATCH_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value
}
