import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import AccountBalanceWallet from '@mui/icons-material/AccountBalanceWallet'
import Dashboard from '@mui/icons-material/Dashboard'
import Menu from '@mui/icons-material/Menu'
import ReceiptLong from '@mui/icons-material/ReceiptLong'
import Sell from '@mui/icons-material/Sell'
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

const DRAWER_WIDTH = 240

const NAV_ITEMS = [
  { to: '/', label: 'Overview', icon: Dashboard, end: true },
  { to: '/transactions', label: 'Transactions', icon: ReceiptLong, end: false },
  { to: '/categorize', label: 'Categorize', icon: Sell, end: false },
  { to: '/import', label: 'Import', icon: UploadFile, end: false },
]

/** Renders the responsive navigation shell around the routed page content. */
export function AppShell() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [drawerOpen, setDrawerOpen] = useState(false)

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      {isMobile ? (
        <AppBar position="fixed" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Toolbar>
            <IconButton edge="start" color="inherit" onClick={() => setDrawerOpen(true)} aria-label="Open navigation">
              <Menu />
            </IconButton>
            <Typography variant="h6" noWrap>
              Finance Dashboard
            </Typography>
          </Toolbar>
        </AppBar>
      ) : null}

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
        {!isMobile ? (
          <Toolbar sx={{ gap: 1.5, px: 2 }}>
            <AccountBalanceWallet color="primary" />
            <Typography variant="h6" noWrap>
              Finance Dashboard
            </Typography>
          </Toolbar>
        ) : null}
        <NavList />
      </Drawer>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          px: { xs: 2, md: 4 },
          py: { xs: 2, md: 4 },
          mt: isMobile ? '64px' : 0,
        }}
      >
        <Outlet />
      </Box>
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
