import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from './AuthContext'

export function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <main className="auth-page"><p role="status">Restoring your session…</p></main>
  if (user) return <Navigate to="/projects" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    const data = new FormData(event.currentTarget)
    try {
      await login(String(data.get('username')), String(data.get('password')))
      navigate('/projects', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Sign in failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="login-heading">
        <div className="eyebrow">Internal workspace</div>
        <h1 id="login-heading">Newsroom Materials</h1>
        <p className="lede">Keep reporting evidence organized, traceable, and ready for the newsroom.</p>
        {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}
        <form onSubmit={submit} className="stack-form">
          <label>
            <span>Username</span>
            <input name="username" autoComplete="username" required />
          </label>
          <label>
            <span>Password</span>
            <input name="password" type="password" autoComplete="current-password" required />
          </label>
          <button className="button button--primary" disabled={submitting} type="submit">
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        {import.meta.env.DEV && (
          <p className="demo-hint">Local demo: <strong>emma</strong> / <strong>demo-password</strong></p>
        )}
      </section>
    </main>
  )
}
