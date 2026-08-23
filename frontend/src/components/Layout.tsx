import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="app-shell">
      <header className="site-header">
        <Link className="brand" to="/projects">
          <span className="brand__mark" aria-hidden="true">N</span>
          <span>Newsroom Materials</span>
        </Link>
        <nav aria-label="Primary" className="primary-nav">
          <Link to="/projects">Projects</Link>
          <span className="user-chip" title="Signed-in user">{user?.username}</span>
          <button
            className="button button--quiet"
            type="button"
            onClick={async () => {
              await logout()
              navigate('/login', { replace: true })
            }}
          >
            Log out
          </button>
        </nav>
      </header>
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
