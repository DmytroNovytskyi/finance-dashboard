import { useEffect, useState } from 'react'

interface FittingRowsOptions {
  /** Selects the rendered data rows; the tallest one sets the row height. */
  rowSelector: string
  /** Optional element inside the container whose height is not available for rows, e.g. a head. */
  reservedSelector?: string
  /**
   * The height a row occupies before any stretching. Supply it for lists whose rows grow to fill
   * the container — measuring those would read back the stretched height and shrink the page on
   * every pass.
   */
  naturalRowHeight?: number
  /** Floor for the count; keep it low, since a floor above what fits reintroduces a scrollbar. */
  min?: number
  max?: number
}

interface FittingRows {
  /** Attach to the scrolling container whose height the rows must fill. */
  containerRef: (node: HTMLElement | null) => void
  rows: number
}

/**
 * How many rows fit in a container, so a paged list can size each page to the space it actually
 * has rather than a fixed count. Measures the rendered rows and the reserved element, and
 * re-measures whenever the container resizes or the row count changes.
 */
export function useFittingRows(rowCount: number, options: FittingRowsOptions): FittingRows {
  const { rowSelector, reservedSelector, naturalRowHeight, min = 1, max = 200 } = options
  const [container, setContainer] = useState<HTMLElement | null>(null)
  const [rows, setRows] = useState(min)

  useEffect(() => {
    if (!container) return
    const measure = () => {
      let rowHeight = naturalRowHeight ?? 0
      if (naturalRowHeight === undefined) {
        for (const row of container.querySelectorAll(rowSelector)) {
          rowHeight = Math.max(rowHeight, row.getBoundingClientRect().height)
        }
      }
      if (rowHeight <= 0) return
      const gap = parseFloat(getComputedStyle(container).rowGap) || 0
      const reserved = reservedSelector
        ? (container.querySelector(reservedSelector)?.getBoundingClientRect().height ?? 0)
        : 0
      const available = container.clientHeight - reserved
      setRows(Math.max(min, Math.min(max, Math.floor((available + gap) / (rowHeight + gap)))))
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(container)
    return () => observer.disconnect()
  }, [container, rowCount, rowSelector, reservedSelector, naturalRowHeight, min, max])

  return { containerRef: setContainer, rows }
}

/**
 * Whether a page of rows should stretch to fill its container. Only a full page should: it has
 * less than one row's height left over, so growing the rows hides a remainder too small to hold
 * another row. A list shorter than a page keeps its natural row height and leaves the space below
 * empty, rather than spreading a few rows over the whole card.
 */
export function stretchesToFill(shown: number, perPage: number): boolean {
  return perPage > 0 && shown >= perPage
}
