import { useEffect, useState } from 'react'

interface FittingRowsOptions {
  /** Selects the rendered data rows; the tallest one sets the row height. */
  rowSelector: string
  /** Optional element inside the container whose height is not available for rows, e.g. a head. */
  reservedSelector?: string
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
  const { rowSelector, reservedSelector, min = 1, max = 200 } = options
  const [container, setContainer] = useState<HTMLElement | null>(null)
  const [rows, setRows] = useState(min)

  useEffect(() => {
    if (!container) return
    const measure = () => {
      let rowHeight = 0
      for (const row of container.querySelectorAll(rowSelector)) {
        rowHeight = Math.max(rowHeight, row.getBoundingClientRect().height)
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
  }, [container, rowCount, rowSelector, reservedSelector, min, max])

  return { containerRef: setContainer, rows }
}
