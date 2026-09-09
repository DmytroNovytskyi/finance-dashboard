import { createTheme, useColorScheme } from '@mui/material/styles'

/**
 * Dark-leaning charcoal look with a muted indigo accent. Charts always carry a labeled
 * legend/list as the identity channel alongside the categorical colors.
 */

export type Scheme = 'light' | 'dark'

/** Categorical slots, used to color a category that has no color of its own. */
export const categoricalPalette: Record<Scheme, string[]> = {
  light: ['#5b78c9', '#2f9c8f', '#c79b3f', '#b24f6e', '#8a63a8', '#d0654f', '#4d8f68', '#5aa0c9'],
  dark: ['#6f8ad4', '#38a89b', '#d2a94e', '#c25c79', '#9a74ba', '#dc735e', '#57a077', '#66aed2'],
}

/** Color reserved for the "(uncategorized)" bucket. */
export const uncategorizedColor: Record<Scheme, string> = {
  light: '#8a9099',
  dark: '#9aa3ae',
}

/** Text colors for income (money in) and expense (money out) amounts. */
export const amountColor: Record<Scheme, { income: string; expense: string }> = {
  light: { income: '#2b8f62', expense: '#b95948' },
  dark: { income: '#4bb184', expense: '#d67a68' },
}

/** Surfaces and ink used for chart chrome (axes, grid, tooltip text). */
export const chartInk: Record<Scheme, { grid: string; axis: string; textSecondary: string }> = {
  light: { grid: '#e1e6ec', axis: '#c6cdd7', textSecondary: '#5f6875' },
  dark: { grid: '#2a303a', axis: '#464e5a', textSecondary: '#b7c0cd' },
}

const baseColors = {
  light: { page: '#f2f4f7', surface: '#fbfcfe', primary: '#4558a8' },
  dark: { page: '#111318', surface: '#1c2027', primary: '#6c7ee0' },
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
