import { useEffect, useState } from 'react'
import {
  DATE_RANGE_PRESETS,
  modeForRange,
  rangeForPreset,
  type DateMode,
  type DateRange,
  type DateRangePreset,
} from '../../lib/date'
import type { StatisticsGranularity } from '../../types'

/** A period: the preset it came from, the range it resolves to, and how it is being written. */
export interface PeriodSelection {
  preset: DateRangePreset
  range: DateRange
  mode: DateMode
}

/**
 * How a chart divides time. Every value but {@code transaction} is a bucket the statistics group
 * by; {@code transaction} is not a bucket at all — it draws one point per transaction, which only
 * the per-category series chart can do.
 */
export type TrendGranularity = 'transaction' | StatisticsGranularity

export const GRANULARITY_OPTIONS: { value: TrendGranularity; label: string }[] = [
  { value: 'transaction', label: 'Transaction' },
  { value: 'day', label: 'Day' },
  { value: 'week', label: 'Week' },
  { value: 'month', label: 'Month' },
  { value: 'quarter', label: 'Quarter' },
  { value: 'year', label: 'Year' },
]

/** The label of one granularity, for the places that name it in prose. */
export function granularityLabel(value: TrendGranularity): string {
  return GRANULARITY_OPTIONS.find((option) => option.value === value)?.label ?? value
}

export const DEFAULT_PERIOD: PeriodSelection = {
  preset: 'thisYear',
  range: rangeForPreset('thisYear'),
  mode: 'dates',
}

/** Month buckets: the one granularity that reads sensibly on both statistics pages. */
export const DEFAULT_GRANULARITY: TrendGranularity = 'month'

/** One stored record rather than one per page: the statistics pages share period and granularity. */
const STORAGE_KEY = 'finance-dashboard.period.v1'

/** Read when the shared key is absent, so choices made before the pages shared them survive. */
const LEGACY_STORAGE_KEY = 'finance-dashboard.overview.v1'

const PRESET_VALUES: DateRangePreset[] = [...DATE_RANGE_PRESETS.map((option) => option.value), 'custom']
const DATE_MODES: DateMode[] = ['month', 'dates']

interface StoredPreferences {
  preset?: unknown
  from?: unknown
  to?: unknown
  mode?: unknown
  granularity?: unknown
}

interface LoadedPreferences {
  period: PeriodSelection
  granularity: TrendGranularity
}

const FALLBACK: LoadedPreferences = { period: DEFAULT_PERIOD, granularity: DEFAULT_GRANULARITY }

function readPreferences(): LoadedPreferences {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY) ?? window.localStorage.getItem(LEGACY_STORAGE_KEY)
    if (!raw) return FALLBACK
    const stored = JSON.parse(raw) as StoredPreferences
    const granularity = GRANULARITY_OPTIONS.some((option) => option.value === stored.granularity)
      ? (stored.granularity as TrendGranularity)
      : DEFAULT_GRANULARITY
    if (typeof stored.preset !== 'string' || !PRESET_VALUES.includes(stored.preset as DateRangePreset)) {
      return { period: DEFAULT_PERIOD, granularity }
    }
    const preset = stored.preset as DateRangePreset
    const range: DateRange =
      preset === 'custom'
        ? {
            from: typeof stored.from === 'string' ? stored.from : null,
            to: typeof stored.to === 'string' ? stored.to : null,
          }
        : rangeForPreset(preset)
    const mode =
      typeof stored.mode === 'string' && (DATE_MODES as string[]).includes(stored.mode)
        ? (stored.mode as DateMode)
        : modeForRange(range)
    return { period: { preset, range, mode }, granularity }
  } catch {
    return FALLBACK
  }
}

/**
 * The period and granularity the statistics pages share. Setting either on the overview and
 * switching to the trends must not silently change what is being looked at, so both pages read and
 * write this one record.
 */
export function useStatisticsPreferences(): {
  period: PeriodSelection
  granularity: TrendGranularity
  setPeriod: (period: PeriodSelection) => void
  setGranularity: (granularity: TrendGranularity) => void
} {
  const [preferences, setPreferences] = useState<LoadedPreferences>(readPreferences)

  useEffect(() => {
    try {
      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          preset: preferences.period.preset,
          from: preferences.period.range.from,
          to: preferences.period.range.to,
          mode: preferences.period.mode,
          granularity: preferences.granularity,
        }),
      )
    } catch {
      /* storage unavailable: keep the selection for this session only */
    }
  }, [preferences])

  return {
    period: preferences.period,
    granularity: preferences.granularity,
    setPeriod: (period) => setPreferences((current) => ({ ...current, period })),
    setGranularity: (granularity) => setPreferences((current) => ({ ...current, granularity })),
  }
}
