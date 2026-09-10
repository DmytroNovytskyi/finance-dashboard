import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

interface PageHeaderProps {
  title: string
  subtitle: string
  /** Optional control rendered at the end of the title row. */
  action?: ReactNode
}

/** Page title block: the heading and one-line description every page opens with. */
export function PageHeader({ title, subtitle, action }: PageHeaderProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: 'space-between',
        gap: 2,
        flexWrap: 'wrap',
      }}
    >
      <Box>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          {title}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {subtitle}
        </Typography>
      </Box>
      {action}
    </Box>
  )
}

/**
 * Page root. Fills the shell exactly, so a page lays out its own regions against the viewport
 * height instead of growing the document and scrolling the whole window.
 */
export function PageShell({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minHeight: 0 }}>
      {children}
    </Box>
  )
}

/**
 * The flexible region of a page. It takes whatever height the fixed regions above leave; content
 * that wants to fill that space uses `flex: '1 0 auto'` with a minHeight, so it stretches on a
 * tall window and the region scrolls on its own when it cannot fit — the window never does.
 */
export function PageScroll({ children }: { children: ReactNode }) {
  return (
    <Box
      sx={{
        flex: 1,
        minHeight: 0,
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
    >
      {children}
    </Box>
  )
}
