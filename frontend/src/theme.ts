import { createTheme, useColorScheme } from '@mui/material/styles'

/**
 * Shared visual tokens for light and dark mode. Surfaces, ink, and the categorical
 * palette follow the validated data-viz palette (dataviz skill); the MUI theme maps
 * its backgrounds to those chart surfaces so a Card is a valid chart canvas.
 */

export type Scheme = 'light' | 'dark'

/** Categorical slots, used to color a category that has no color of its own. */
export const categoricalPalette: Record<Scheme, string[]> = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'],
}

/** Color reserved for the "(uncategorized)" bucket. */
export const uncategorizedColor: Record<Scheme, string> = {
  light: '#898781',
  dark: '#9a9891',
}

/** Text colors for income (money in) and expense (money out) amounts. */
export const amountColor: Record<Scheme, { income: string; expense: string }> = {
  light: { income: '#006300', expense: '#b3261e' },
  dark: { income: '#0ca30c', expense: '#ff8f86' },
}

/** Surfaces and ink used for chart chrome (axes, grid, tooltip text). */
export const chartInk: Record<Scheme, { grid: string; axis: string; textSecondary: string }> = {
  light: { grid: '#e1e0d9', axis: '#c3c2b7', textSecondary: '#52514e' },
  dark: { grid: '#2c2c2a', axis: '#383835', textSecondary: '#c3c2b7' },
}

const baseColors = {
  light: {
    page: '#f9f9f7',
    surface: '#fcfcfb',
    primary: '#1c5cab',
  },
  dark: {
    page: '#0d0d0d',
    surface: '#1a1a19',
    primary: '#3987e5',
  },
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
