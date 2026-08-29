import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from './AuthContext'

const USERNAME_PATTERN = /^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$/
const USERNAME_ERROR = 'Username must be 3–32 characters, start and end with a letter or number, and use only letters, numbers, dots, underscores, or hyphens. Spaces and other special characters are not allowed.'

export function CreateAccountPage() {
  const { user, loading, register } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [usernameError, setUsernameError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <main className="auth-page"><p role="status">Restoring your session…</p></main>
  if (user) return <Navigate to="/projects" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setUsernameError('')
    const data = new FormData(event.currentTarget)
    const username = String(data.get('username'))
    const password = String(data.get('password'))
    const confirmation = String(data.get('passwordConfirmation'))
    if (username.length < 3 || username.length > 32 || !USERNAME_PATTERN.test(username)) {
      setUsernameError(USERNAME_ERROR)
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
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
        <form onSubmit={submit} className="stack-form" noValidate>
          <label>
            <span>Username</span>
            <input
              name="username"
              autoComplete="username"
              minLength={3}
              maxLength={32}
              aria-describedby={usernameError ? 'username-guidance username-error' : 'username-guidance'}
              aria-invalid={Boolean(usernameError)}
              onChange={() => setUsernameError('')}
              required
            />
            <small id="username-guidance">
              Allowed: 3–32 letters (A–Z and a–z), numbers, dots, underscores, and hyphens.
              {' '}Not allowed: spaces or other special characters. Start and end with a letter or number.
            </small>
            {usernameError && <small id="username-error" className="field-error" role="alert">{usernameError}</small>}
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
