import { useState } from 'react'
import { Link as RouterLink, NavLink, Outlet, useLocation } from 'react-router-dom'
import AccountBalanceWallet from '@mui/icons-material/AccountBalanceWallet'
import AccountBox from '@mui/icons-material/AccountBox'
import Dashboard from '@mui/icons-material/Dashboard'
import Menu from '@mui/icons-material/Menu'
import ReceiptLong from '@mui/icons-material/ReceiptLong'
import Sell from '@mui/icons-material/Sell'
import ShowChart from '@mui/icons-material/ShowChart'
import UploadFile from '@mui/icons-material/UploadFile'
import AppBar from '@mui/material/AppBar'
import Box from '@mui/material/Box'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { CurrencySelect } from '../features/overview/CurrencySelect'
import { useDisplayCurrency } from '../features/preferences/displayCurrency'

const DRAWER_WIDTH = 280

const NAV_ITEMS = [
  { to: '/', label: 'Overview', icon: Dashboard, end: true },
  { to: '/trends', label: 'Trends', icon: ShowChart, end: false },
  { to: '/transactions', label: 'Transactions', icon: ReceiptLong, end: false },
  { to: '/categorize', label: 'Categorize', icon: Sell, end: false },
  { to: '/import', label: 'Import', icon: UploadFile, end: false },
  { to: '/accounts', label: 'Accounts', icon: AccountBox, end: false },
]

/** Renders the responsive navigation shell around the routed page content. */
export function AppShell() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    <Box
      sx={{
        display: 'flex',
        height: '100dvh',
        overflow: 'hidden',
        bgcolor: 'background.default',
      }}
    >

      <Drawer
        variant={isMobile ? 'temporary' : 'permanent'}
        open={isMobile ? drawerOpen : true}
        onClose={() => setDrawerOpen(false)}
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          {!isMobile ? (
            <Toolbar sx={{ gap: 1.5, px: 2 }}>
              <AccountBalanceWallet color="primary" sx={{ flexShrink: 0 }} />
              <AppTitle />
            </Toolbar>
          ) : null}
          <Box sx={{ flexGrow: 1, overflowY: 'auto' }}>
            <NavList />
          </Box>
          <CurrencyFooter />
        </Box>
      </Drawer>

      <Box
        sx={{
          flexGrow: 1,
          minWidth: 0,
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {isMobile ? (
          <AppBar position="static" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Toolbar>
              <IconButton edge="start" color="inherit" onClick={() => setDrawerOpen(true)} aria-label="Open navigation">
                <Menu />
              </IconButton>
              <AppTitle />
            </Toolbar>
          </AppBar>
        ) : null}

        <Box
          component="main"
          sx={{
            flexGrow: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            px: { xs: 2, md: 4 },
            py: { xs: 2, md: 3 },
            overflowY: 'auto',
          }}
        >
          <Outlet />
        </Box>
      </Box>
    </Box>
  )
}

/** The app title, which doubles as the way back to the overview from any page. */
function AppTitle() {
  return (
    <Typography
      component={RouterLink}
      to="/"
      variant="h6"
      sx={{
        color: 'inherit',
        textDecoration: 'none',
        whiteSpace: 'nowrap',
        '&:hover': { color: 'primary.main' },
      }}
    >
      Finance Dashboard
    </Typography>
  )
}

function CurrencyFooter() {
  const { displayCurrency, setDisplayCurrency } = useDisplayCurrency()
  return (
    <Box
      sx={{
        p: 1.5,
        px: 2,
        borderTop: 1,
        borderColor: 'divider',
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
      }}
    >
      <Typography variant="caption" color="text.secondary">
        Show amounts in
      </Typography>
      <CurrencySelect value={displayCurrency} onChange={setDisplayCurrency} />
    </Box>
  )
}

function NavList() {
  const location = useLocation()
  return (
    <List sx={{ px: 1 }}>
      {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => {
        const selected = end ? location.pathname === to : location.pathname.startsWith(to)
        return (
          <ListItemButton
            key={to}
            component={NavLink}
            to={to}
            end={end}
            selected={selected}
            sx={{ borderRadius: 1.5, mb: 0.5 }}
          >
            <ListItemIcon sx={{ minWidth: 40 }}>
              <Icon />
            </ListItemIcon>
            <ListItemText primary={label} />
          </ListItemButton>
        )
      })}
    </List>
  )
}
