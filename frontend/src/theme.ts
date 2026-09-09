import { createTheme, useColorScheme } from '@mui/material/styles'

/**
 * Warm, muted look. Chart categorical slots fall back to this set when a category has no color of
 * its own; charts always carry a labeled legend/list as the secondary encoding channel.
 */

export type Scheme = 'light' | 'dark'

/** Categorical slots, used to color a category that has no color of its own. */
export const categoricalPalette: Record<Scheme, string[]> = {
  light: ['#c4552d', '#3f8f86', '#d9a538', '#7d5ba6', '#5b9a53', '#c2657c', '#4f7fb0', '#9a6b4a'],
  dark: ['#d4693d', '#3f9d93', '#e0a63e', '#a07bc4', '#7fae64', '#dd7f94', '#6f9bc7', '#bd8f6a'],
}

/** Color reserved for the "(uncategorized)" bucket. */
export const uncategorizedColor: Record<Scheme, string> = {
  light: '#8a857d',
  dark: '#9a958d',
}

/** Text colors for income (money in) and expense (money out) amounts. */
export const amountColor: Record<Scheme, { income: string; expense: string }> = {
  light: { income: '#1e7d3e', expense: '#bf3a2a' },
  dark: { income: '#5fbf77', expense: '#ef7a64' },
}

/** Surfaces and ink used for chart chrome (axes, grid, tooltip text). */
export const chartInk: Record<Scheme, { grid: string; axis: string; textSecondary: string }> = {
  light: { grid: '#e6e0d3', axis: '#c9bfad', textSecondary: '#6b6253' },
  dark: { grid: '#33302a', axis: '#4a453c', textSecondary: '#c9c0b0' },
}

const baseColors = {
  light: { page: '#f7f3ec', surface: '#fdfaf5', primary: '#9e4b28' },
  dark: { page: '#14120f', surface: '#201c17', primary: '#e08a5e' },
}

export const theme = createTheme({
  cssVariables: { colorSchemeSelector: 'media' },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: baseColors.light.primary },
        background: { default: baseColors.light.page, paper: baseColors.light.surface },
        divider: chartInk.light.grid,
      },
    },
    dark: {
      palette: {
        primary: { main: baseColors.dark.primary },
        background: { default: baseColors.dark.page, paper: baseColors.dark.surface },
        divider: chartInk.dark.grid,
      },
    },
  },
  shape: { borderRadius: 10 },
})

/** Resolves the active color scheme, honoring the OS preference (colorSchemeSelector: media). */
export function useScheme(): Scheme {
  const { mode, systemMode } = useColorScheme()
  return (mode ?? systemMode) === 'dark' ? 'dark' : 'light'
}

/** Resolves a category's display color: its own color when set, otherwise the next palette slot. */
export function categoryColorHex(color: string | null, index: number, scheme: Scheme): string {
  if (color) return color
  const palette = categoricalPalette[scheme]
  return palette[index % palette.length]
}
