import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const mobileQuery = () => typeof window.matchMedia === 'function'
  ? window.matchMedia('(max-width: 559px)')
  : null

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [mobile, setMobile] = useState(() => mobileQuery()?.matches ?? false)
  const [navigationOpen, setNavigationOpen] = useState(false)
  const [logoutError, setLogoutError] = useState('')

  useEffect(() => {
    const query = mobileQuery()
    if (!query) return
    const update = (event: MediaQueryListEvent) => {
      setMobile(event.matches)
      if (!event.matches) setNavigationOpen(false)
    }
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])

  async function endSession() {
    setLogoutError('')
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch (caught) {
      setLogoutError(caught instanceof Error ? caught.message : 'Could not end this session.')
    }
  }

  const navigation = (
    <nav id="primary-navigation" aria-label="Primary" className="primary-nav">
      <Link to="/projects">Projects</Link>
      <Link to="/search">Search</Link>
      <Link
        className="user-chip"
        to="/profile"
        aria-label={`Profile for ${user?.username ?? 'user'}`}
        title="Signed-in user"
      >
        {user?.username}
      </Link>
      <button className="button button--quiet" type="button" onClick={endSession}>Log out</button>
    </nav>
  )

  return (
    <div className="app-shell">
      <header className="site-header">
        <Link className="brand" to="/projects">
          <span className="brand__mark" aria-hidden="true">N</span>
          <span>Newsroom Materials</span>
        </Link>
        {mobile ? (
          <>
          <button
            className="button button--quiet mobile-menu"
            type="button"
            aria-expanded={navigationOpen}
            aria-controls="primary-navigation"
            aria-label={navigationOpen ? 'Close navigation' : 'Open navigation'}
            onClick={() => setNavigationOpen((open) => !open)}
          >
            Menu
          </button>
            {navigationOpen && navigation}
          </>
        ) : navigation}
      </header>
      {logoutError && <div role="alert" className="site-message inline-message inline-message--error">{logoutError}</div>}
      <main className="page-shell">{children}</main>
    </div>
  )
}

export function PageState({ children, kind = 'status' }: { children: ReactNode; kind?: 'status' | 'alert' }) {
  return <div className={`page-state page-state--${kind}`} role={kind}>{children}</div>
}

export function TagList({ tags, kind }: { tags: string[]; kind: 'manual' | 'ai' }) {
  if (!tags.length) return null
  return (
    <ul className="tag-list" aria-label={kind === 'manual' ? 'Manual tags' : 'AI tags'}>
      {tags.map((tag) => <li className={`tag tag--${kind}`} key={tag}>{tag}</li>)}
    </ul>
  )
}
