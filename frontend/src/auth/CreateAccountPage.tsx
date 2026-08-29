import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from './AuthContext'

export function CreateAccountPage() {
  const { user, loading, register } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <main className="auth-page"><p role="status">Restoring your session…</p></main>
  if (user) return <Navigate to="/projects" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const data = new FormData(event.currentTarget)
    const username = String(data.get('username'))
    const password = String(data.get('password'))
    const confirmation = String(data.get('passwordConfirmation'))
    if (password !== confirmation) {
      setError('Passwords do not match.')
      return
    }
    setSubmitting(true)
    try {
      await register(username, password)
      navigate('/projects', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Account creation failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="register-heading">
        <div className="eyebrow">Internal workspace</div>
        <h1 id="register-heading">Create your account</h1>
        <p className="lede">Use one account to add, manage, and trace reporting materials.</p>
        {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}
        <form onSubmit={submit} className="stack-form">
          <label>
            <span>Username</span>
            <input
              name="username"
              autoComplete="username"
              minLength={3}
              maxLength={32}
              pattern="[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?"
              required
            />
            <small>3–32 lowercase letters, numbers, dots, underscores, or hyphens.</small>
          </label>
          <label>
            <span>Password</span>
            <input name="password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required />
            <small>Use at least 8 characters.</small>
          </label>
          <label>
            <span>Confirm password</span>
            <input name="passwordConfirmation" type="password" autoComplete="new-password" minLength={8} maxLength={72} required />
          </label>
          <button className="button button--primary" disabled={submitting} type="submit">
            {submitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p>
      </section>
    </main>
  )
}
