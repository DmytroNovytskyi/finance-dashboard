import { useEffect, useEffectEvent, useRef } from 'react'

interface PageOnWheelOptions {
  onNext: () => void
  onPrevious: () => void
  canNext: boolean
  canPrevious: boolean
  /**
   * Whether to listen at all. False installs no listener rather than ignoring events: a wheel
   * listener that cannot prevent the default makes the compositor wait on the main thread for
   * every wheel event over the element, so a list with one page should not have one.
   */
  enabled: boolean
}

/**
 * How far the wheel must turn before it counts as one step. A mouse notch is around 100 pixels, so
 * this is far below one notch: a single notch always turns exactly one page and no step is ever
 * swallowed. A trackpad has no steps at all, so a momentum flick crosses this repeatedly.
 */
const STEP_PX = 40

/** Blink converts wheel messages to lines at 40 pixels each; the DOM event's own line height is unreliable. */
const PIXELS_PER_LINE = 40

/** Slack allowed when asking whether the container itself can scroll, so rounding cannot disable paging. */
const SCROLL_TOLERANCE_PX = 4

/**
 * One event's vertical distance in pixels. Browsers report pixels, lines or pages, and only the
 * pixels case is reachable from Chrome and Edge, so the other two exist for other engines.
 */
function verticalPixels(event: WheelEvent): number {
  if (event.deltaMode === WheelEvent.DOM_DELTA_LINE) return event.deltaY * PIXELS_PER_LINE
  if (event.deltaMode === WheelEvent.DOM_DELTA_PAGE) {
    const page = event.view?.innerHeight ?? 0
    return page > 0 ? event.deltaY * page : event.deltaY
  }
  return event.deltaY
}

/** Whether the container can scroll itself, in which case the wheel belongs to it and not to us. */
function scrollsItself(container: HTMLElement): boolean {
  return container.scrollHeight - container.clientHeight > SCROLL_TOLERANCE_PX
}

/**
 * Whether the event started inside something that scrolls by itself, so a scroll area nested in a
 * list is never hijacked by the list's paging.
 */
function startsInNestedScroller(event: WheelEvent, container: HTMLElement): boolean {
  let node = event.target as HTMLElement | null
  while (node && node !== container) {
    if (node.scrollHeight - node.clientHeight > SCROLL_TOLERANCE_PX) return true
    node = node.parentElement
  }
  return false
}

/**
 * Turns pages when the wheel is scrolled over a paged list.
 *
 * A mouse notch turns exactly one page: the distances accumulate and the total is reset on every
 * turn, so two notches advance two pages and three advance three. The total is reset rather than
 * reduced by the step because a remainder would double-count — a 100 pixel notch against a 40
 * pixel step would leave 60, which crosses again and turns a second page for the same notch.
 *
 * A trackpad has no notches, so a flick with momentum will turn several pages. That is the cost of
 * an exact per-step mapping; suppressing it would need a pause-based rule, which also collapses a
 * fast mouse spin into a single page.
 *
 * The listener is attached natively and not through `onWheel` because React registers wheel as a
 * passive listener on the root, where `preventDefault` is a no-op.
 */
export function usePageOnWheel(container: HTMLElement | null, options: PageOnWheelOptions): void {
  const { onNext, onPrevious, canNext, canPrevious, enabled } = options
  const travelled = useRef(0)

  const handle = useEffectEvent((event: WheelEvent) => {
    if (event.ctrlKey || event.metaKey) return
    const distance = verticalPixels(event)
    if (Math.abs(distance) <= Math.abs(event.deltaX)) return
    if (startsInNestedScroller(event, container!)) return
    travelled.current += distance
    if (Math.abs(travelled.current) < STEP_PX) return
    const forward = travelled.current > 0
    if (forward ? !canNext : !canPrevious) {
      travelled.current = 0
      return
    }
    if (scrollsItself(container!)) {
      travelled.current = 0
      return
    }
    travelled.current = 0
    if (event.cancelable) event.preventDefault()
    if (forward) onNext()
    else onPrevious()
  })

  useEffect(() => {
    travelled.current = 0
  }, [container, enabled])

  useEffect(() => {
    if (!container || !enabled) return
    const listener = (event: WheelEvent) => handle(event)
    container.addEventListener('wheel', listener, { passive: false })
    return () => container.removeEventListener('wheel', listener)
  }, [container, enabled])
}
