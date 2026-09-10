import { useEffect, useState } from 'react'

interface FittingRowsOptions {
  /** Selects the rendered data rows; the tallest one sets the row height. */
  rowSelector: string
  /** Optional element inside the container whose height is not available for rows, e.g. a head. */
  reservedSelector?: string
  /**
   * The height a row occupies when it is not sharing the container. Supply it for lists whose rows
   * grow to fill: measuring those would read back the grown height and shrink the page on every
   * pass, and the height the rows take is derived from it rather than measured. Without it the
   * rows keep their rendered height and only {@link FittingRows.rows} is useful.
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
  /**
   * The height to give each row, or null when no natural height was supplied. It is worked out
   * from how many rows the container could hold, not from how many it is actually given, so a
   * short list keeps the same row height as a full one and simply leaves the rest of the space
   * empty. Deriving it from the rows on screen instead would make rows jump as the list changes.
   */
  rowHeight: number | null
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
  const [rowHeight, setRowHeight] = useState<number | null>(null)

  useEffect(() => {
    if (!container) return
    const measure = () => {
      let measured = naturalRowHeight ?? 0
      if (naturalRowHeight === undefined) {
        for (const row of container.querySelectorAll(rowSelector)) {
          measured = Math.max(measured, row.getBoundingClientRect().height)
        }
      }
      if (measured <= 0) return
      const gap = parseFloat(getComputedStyle(container).rowGap) || 0
      const reserved = reservedSelector
        ? (container.querySelector(reservedSelector)?.getBoundingClientRect().height ?? 0)
        : 0
      const available = container.clientHeight - reserved
      const fitting = Math.max(min, Math.min(max, Math.floor((available + gap) / (measured + gap))))
      setRows(fitting)
      setRowHeight(
        naturalRowHeight === undefined || fitting <= 0
          ? null
          : (available - (fitting - 1) * gap) / fitting,
      )
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(container)
    return () => observer.disconnect()
  }, [container, rowCount, rowSelector, reservedSelector, naturalRowHeight, min, max])

  return { containerRef: setContainer, rows, rowHeight }
}
