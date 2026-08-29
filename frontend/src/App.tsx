import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { CreateAccountPage } from './auth/CreateAccountPage'
import { LoginPage } from './auth/LoginPage'
import { ProfilePage } from './auth/ProfilePage'
import { Layout, PageState } from './components/Layout'
import { MaterialPage } from './materials/MaterialPage'
import { NewMaterialPage } from './materials/NewMaterialPage'
import { VersionEditorPage } from './materials/VersionEditorPage'
import { ProjectDetailPage } from './projects/ProjectDetailPage'
import { ProjectsPage } from './projects/ProjectsPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <PageState>Restoring your session…</PageState>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Layout>{children}</Layout>
}

export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<CreateAccountPage />} />
        <Route path="/profile" element={<RequireAuth><ProfilePage /></RequireAuth>} />
        <Route path="/projects" element={<RequireAuth><ProjectsPage /></RequireAuth>} />
        <Route path="/projects/:id" element={<RequireAuth><ProjectDetailPage /></RequireAuth>} />
        <Route path="/projects/:id/new" element={<RequireAuth><NewMaterialPage /></RequireAuth>} />
        <Route path="/materials/:id" element={<RequireAuth><MaterialPage /></RequireAuth>} />
        <Route path="/materials/:id/edit" element={<RequireAuth><VersionEditorPage /></RequireAuth>} />
        <Route path="/materials/:id/versions/:number" element={<RequireAuth><MaterialPage /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Routes>
    </AuthProvider>
  )
}
