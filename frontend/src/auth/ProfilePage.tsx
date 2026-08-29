import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from './AuthContext'

export function ProfilePage() {
  const { user } = useAuth()
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSuccess('')
    const form = event.currentTarget
    const data = new FormData(form)
    const currentPassword = String(data.get('currentPassword'))
    const newPassword = String(data.get('newPassword'))
    const confirmation = String(data.get('newPasswordConfirmation'))
    if (newPassword !== confirmation) {
      setError('New passwords do not match.')
      return
    }
    setSubmitting(true)
    try {
      await api.changePassword(currentPassword, newPassword)
      form.reset()
      setSuccess('Password updated successfully.')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Password update failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page page--narrow">
      <section className="page-heading">
        <div>
          <p className="eyebrow">Account settings</p>
          <h1>Your profile</h1>
          <p>View your account identity and keep your sign-in credentials current.</p>
        </div>
      </section>
      <section className="panel profile-panel" aria-labelledby="account-heading">
        <h2 id="account-heading">Account</h2>
        <label>
          <span>Username</span>
          <input value={user?.username ?? ''} readOnly aria-readonly="true" />
          <small>Your username identifies the materials and versions you create.</small>
        </label>
      </section>
      <section className="panel profile-panel" aria-labelledby="password-heading">
        <h2 id="password-heading">Change password</h2>
        <p className="muted">Confirm your current password before choosing a new one.</p>
        {error && <div role="alert" className="inline-message inline-message--error">{error}</div>}
        {success && <div role="status" className="inline-message inline-message--success">{success}</div>}
        <form onSubmit={submit} className="stack-form">
          <label>
            <span>Current password</span>
            <input name="currentPassword" type="password" autoComplete="current-password" required />
          </label>
          <label>
            <span>New password</span>
            <input name="newPassword" type="password" autoComplete="new-password" minLength={8} maxLength={72} required />
            <small>Use at least 8 characters.</small>
          </label>
          <label>
            <span>Confirm new password</span>
            <input name="newPasswordConfirmation" type="password" autoComplete="new-password" minLength={8} maxLength={72} required />
          </label>
          <div className="form-actions">
            <button className="button button--primary" disabled={submitting} type="submit">
              {submitting ? 'Updating password…' : 'Update password'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}
