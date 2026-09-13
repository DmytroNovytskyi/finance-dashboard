import { useEffect, useState } from 'react'

/** Floor for one card, so a short window still leaves the plot inside it readable. */
const MIN_CARD_HEIGHT = 280

interface FittingCardHeight {
  /** Attach to the scrolling region the card rows are laid out in. */
  containerRef: (node: HTMLElement | null) => void
  /** The height to give every card, so {@link useFittingCardHeight}'s rows fill the container. */
  cardHeight: number
}

/**
 * The height to give each of `desiredRows` equal rows of cards so that many fill the container
 * instead of overflowing it — the grid sibling of {@link useFittingRows}, for content that is sized
 * by a fixed height rather than by a row count.
 *
 * It measures the container alone. Reading the cards back would feed their own height into the
 * answer that produced it, and each pass would shrink the container a little further.
 *
 * The floor is a floor, not a fit: on a window too short for the rows the region scrolls, which is
 * the same bargain the paged lists make when their pagination bar does not fit.
 */
export function useFittingCardHeight(
  desiredRows: number,
  gap: number,
  minHeight: number = MIN_CARD_HEIGHT,
): FittingCardHeight {
  const [container, setContainer] = useState<HTMLElement | null>(null)
  const [cardHeight, setCardHeight] = useState(minHeight)

  useEffect(() => {
    if (!container) return
    const measure = () => {
      const usable = container.clientHeight - gap * (desiredRows - 1)
      setCardHeight(Math.max(minHeight, Math.floor(usable / desiredRows)))
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(container)
    return () => observer.disconnect()
  }, [container, desiredRows, gap, minHeight])

  return { containerRef: setContainer, cardHeight }
}
