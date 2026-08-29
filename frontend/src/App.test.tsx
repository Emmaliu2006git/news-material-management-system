import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { App } from './App'
import type { Material } from './api/types'
import {
  fileMaterial,
  historicalTextMaterial,
  installApi,
  json,
  project,
  textMaterial,
} from './test/fixtures'

function renderRoute(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <App />
    </MemoryRouter>,
  )
}

describe('authentication shell', () => {
  it('shows a clear error for invalid credentials and supports a successful login', async () => {
    const api = installApi({ me: null })
    api.mockImplementation((input, init) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) return json({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' })
      if (url.endsWith('/auth/me')) return json({ code: 'AUTHENTICATION_REQUIRED', message: 'Authentication required', requestId: 'req', details: [] }, 401)
      if (url.endsWith('/auth/login')) {
        const body = JSON.parse(String(init?.body))
        return body.password === 'correct'
          ? json({ id: 'user-emma', username: 'emma' })
          : json({ code: 'INVALID_CREDENTIALS', message: 'Invalid username or password', requestId: 'req', details: [] }, 401)
      }
      if (url.endsWith('/projects')) return json([project])
      return json({}, 404)
    })
    const user = userEvent.setup()
    renderRoute('/login')

    await user.type(await screen.findByLabelText(/username/i), 'emma')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')

    await user.clear(screen.getByLabelText(/password/i))
    await user.type(screen.getByLabelText(/password/i), 'correct')
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByRole('heading', { name: /reporting projects/i })).toBeInTheDocument()
  })

  it('restores a session and exposes responsive navigation and logout', async () => {
    installApi()
    renderRoute('/projects')

    expect(await screen.findByText('Newsroom Materials')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /primary/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /projects/i })).toHaveAttribute('href', '/projects')
    expect(screen.getByRole('button', { name: /log out/i })).toBeInTheDocument()
  })

  it('keeps the authenticated shell visible and explains a failed logout', async () => {
    const api = installApi()
    api.mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return json({ id: 'user-emma', username: 'emma' })
      if (url.endsWith('/auth/csrf')) return json({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' })
      if (url.endsWith('/auth/logout')) {
        return json({ code: 'LOGOUT_FAILED', message: 'Could not end this session', requestId: 'req', details: [] }, 500)
      }
      if (url.endsWith('/projects')) return json([project])
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/projects')

    await actor.click(await screen.findByRole('button', { name: /log out/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not end this session/i)
    expect(screen.getByText('emma')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /reporting projects/i })).toBeInTheDocument()
  })

  it('uses an expandable navigation control at a real mobile media-query boundary', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({
      matches: query === '(max-width: 559px)',
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
    installApi()
    const actor = userEvent.setup()
    renderRoute('/projects')

    const menu = await screen.findByRole('button', { name: /open navigation/i })
    expect(menu).toHaveAttribute('aria-expanded', 'false')
    await actor.click(menu)
    expect(menu).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('navigation', { name: /primary/i })).toBeInTheDocument()
  })
})

describe('projects and current-only search', () => {
  it('lists and creates reporting projects', async () => {
    const api = installApi()
    api.mockImplementation((input, init) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return json({ id: 'user-emma', username: 'emma' })
      if (url.endsWith('/auth/csrf')) return json({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' })
      if (url.endsWith('/projects') && init?.method === 'POST') {
        return json({ ...project, id: 'project-fire', title: 'Warehouse fire' }, 201)
      }
      if (url.endsWith('/projects')) return json([project])
      return json({}, 404)
    })
    const user = userEvent.setup()
    renderRoute('/projects')

    const existingProjectLink = await screen.findByRole('link', { name: 'Open project Downtown flood' })
    expect(existingProjectLink).toHaveAttribute('href', '/projects/project-flood')
    expect(existingProjectLink).toHaveClass('project-card')

    await user.type(screen.getByLabelText(/new project title/i), 'Warehouse fire')
    await user.click(screen.getByRole('button', { name: /create project/i }))

    const createdProjectLink = await screen.findByRole('link', { name: 'Open project Warehouse fire' })
    expect(createdProjectLink).toHaveAttribute('href', '/projects/project-fire')
    expect(createdProjectLink).toHaveClass('project-card')
  })

  it('submits current-version filters and marks an AI image match', async () => {
    const api = installApi({
      search: [{
        id: 'material-file',
        projectId: project.id,
        ownerId: 'user-emma',
        ownerUsername: 'emma',
        type: 'FILE',
        materialCreatedTime: '2026-08-22T14:05:00Z',
        currentVersionId: 'version-file-1',
        currentVersionNumber: 1,
        title: 'Street evidence',
        eventTime: null,
        location: 'East entrance',
        manualTags: ['field'],
        matchedFileId: 'file-photo',
      }],
    })
    const user = userEvent.setup()
    renderRoute('/projects/project-flood')

    expect(await screen.findByRole('heading', { name: 'Downtown flood' })).toBeInTheDocument()
    await user.type(screen.getByLabelText(/search current materials/i), 'flooded street')
    await user.selectOptions(screen.getByLabelText(/material type/i), 'FILE')
    await user.click(screen.getByRole('button', { name: /^search$/i }))

    expect(await screen.findByRole('link', { name: 'Street evidence' })).toBeInTheDocument()
    expect(screen.getByText(/matched an ai-analyzed image/i)).toBeInTheDocument()
    expect(api).toHaveBeenCalledWith(
      expect.stringContaining('q=flooded+street'),
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('composes every supported filter with inclusive date boundaries and owner choices from initial results', async () => {
    const requests: string[] = []
    const results = [
      {
        id: 'material-file',
        projectId: project.id,
        ownerId: 'user-emma',
        ownerUsername: 'emma',
        type: 'FILE',
        materialCreatedTime: '2026-08-22T14:05:00Z',
        currentVersionId: 'version-file-1',
        currentVersionNumber: 1,
        title: 'Street evidence',
        eventTime: '2026-08-21T12:00:00Z',
        location: 'East entrance',
        manualTags: ['field'],
        matchedFileId: null,
      },
      {
        id: 'material-text',
        projectId: project.id,
        ownerId: 'user-alex',
        ownerUsername: 'alex',
        type: 'TEXT',
        materialCreatedTime: '2026-08-20T10:00:00Z',
        currentVersionId: 'version-text-2',
        currentVersionNumber: 2,
        title: 'River update',
        eventTime: null,
        location: null,
        manualTags: [],
        matchedFileId: null,
      },
    ]
    const api = installApi({ search: results })
    api.mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return json({ id: 'user-emma', username: 'emma' })
      if (url.endsWith('/projects')) return json([project])
      if (url.includes('/projects/project-flood/materials')) {
        requests.push(url)
        return json(results)
      }
      return json({}, 404)
    })
    const actor = userEvent.setup()
    renderRoute('/projects/project-flood')

    await actor.selectOptions(await screen.findByLabelText(/owner/i), 'user-emma')
    await actor.type(screen.getByLabelText(/search current materials/i), 'flood warning')
    await actor.selectOptions(screen.getByLabelText(/material type/i), 'FILE')
    await actor.type(screen.getByLabelText(/^location$/i), 'East entrance')
    await actor.type(screen.getByLabelText(/^tag$/i), 'field')
    const dateFilters = [
      [screen.getByLabelText(/material created from/i), '08/01/2026'],
      [screen.getByLabelText(/material created to/i), '08/02/2026'],
      [screen.getByLabelText(/event time from/i), '08/03/2026'],
      [screen.getByLabelText(/event time to/i), '08/04/2026'],
    ] as const
    for (const [input, value] of dateFilters) {
      expect(input).toHaveAttribute('type', 'text')
      expect(input).toHaveAttribute('placeholder', 'MM/DD/YYYY')
      await actor.type(input, value)
    }
    await actor.click(screen.getByRole('button', { name: /^search$/i }))

    const query = new URL(requests.at(-1)!, 'http://localhost').searchParams
    expect(Object.fromEntries(query)).toEqual({
      q: 'flood warning',
      materialType: 'FILE',
      ownerId: 'user-emma',
      createdFrom: '2026-08-01T00:00:00.000Z',
      createdTo: '2026-08-02T23:59:59.999Z',
      eventFrom: '2026-08-03T00:00:00.000Z',
      eventTo: '2026-08-04T23:59:59.999Z',
      location: 'East entrance',
      tag: 'field',
    })
  })
})

describe('material snapshots and permissions', () => {
  it('uses the complete snapshot view for the current text version', async () => {
    installApi({ material: textMaterial })
    renderRoute('/materials/material-text')

    expect(await screen.findByRole('heading', { name: 'River level update' })).toBeInTheDocument()
    expect(screen.getByText('Water reached the second marker.')).toBeInTheDocument()
    const snapshot = screen.getAllByRole('article')[0]
    expect(within(snapshot).getByText(/version 2/i)).toBeInTheDocument()
    expect(screen.getByText(/south bridge/i)).toBeInTheDocument()
    expect(screen.getAllByText(/Aug 22, 2026/i).length).toBeGreaterThan(0)
    expect(screen.getByText('urgent')).toHaveClass('tag--manual')
    expect(screen.getByRole('link', { name: /create new version/i })).toBeInTheDocument()
  })

  it('shows complete human-readable material, version, file, and analysis metadata', async () => {
    const files = fileMaterial.version.snapshot.content.kind === 'FILE'
      ? fileMaterial.version.snapshot.content.files.map((file) => ({ ...file, uploaderUsername: 'emma' }))
      : []
    const complete = {
      ...fileMaterial,
      projectTitle: 'Downtown flood',
      ownerUsername: 'emma',
      version: {
        ...fileMaterial.version,
        creatorUsername: 'emma',
        snapshot: {
          ...fileMaterial.version.snapshot,
          content: {
            kind: 'FILE',
            textContent: 'Photos from the east entrance.',
            files,
          },
        },
      },
    } as Material
    installApi({ material: complete })
    renderRoute('/materials/material-file')

    await screen.findByRole('heading', { name: 'Street evidence' })
    for (const value of ['Downtown flood', 'emma']) {
      expect(screen.getAllByText(value).length).toBeGreaterThan(0)
    }
    const system = screen.getByRole('region', { name: /^system details$/i })
    for (const value of [
      'material-file',
      'project-flood',
      'user-emma',
      'version-file-1',
    ]) {
      expect(within(system).getAllByText(value).length).toBeGreaterThan(0)
    }
    const photo = screen.getByRole('article', { name: /street.jpg/i })
    const fileSystem = within(photo).getByRole('region', { name: /file system details/i })
    for (const value of ['file-photo', 'user-emma', 'a'.repeat(64)]) {
      expect(within(fileSystem).getByText(value)).toBeInTheDocument()
    }
    expect(within(photo).getByText(/generated/i)).toBeInTheDocument()
    expect(within(photo).getAllByText(/aug 22, 2026/i).length).toBeGreaterThan(0)
  })

  it('renders the same snapshot layout read-only for history', async () => {
    installApi({ historical: historicalTextMaterial })
    renderRoute('/materials/material-text/versions/1')

    expect(await screen.findByRole('heading', { name: 'Initial river level' })).toBeInTheDocument()
    expect(screen.getByText('Water reached the first marker.')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: /^system details$/i })).toHaveTextContent('user-emma')
    expect(screen.getByRole('region', { name: /^system details$/i })).toHaveTextContent('version-text-1')
    expect(screen.getByRole('status')).toHaveTextContent(/historical version.*read-only/i)
    expect(screen.queryByRole('link', { name: /create new version/i })).not.toBeInTheDocument()
  })

  it('hides owner controls when the server denies editing', async () => {
    installApi({ material: { ...textMaterial, canEdit: false } })
    renderRoute('/materials/material-text')

    await screen.findByRole('heading', { name: 'River level update' })
    expect(screen.queryByRole('link', { name: /create new version/i })).not.toBeInTheDocument()
  })

  it('shows per-image AI results separately, supports retry, and downloads every file type', async () => {
    const api = installApi({ material: fileMaterial })
    api.mockImplementation((input) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return json({ id: 'user-emma', username: 'emma' })
      if (url.endsWith('/auth/csrf')) return json({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' })
      if (url.endsWith('/materials/material-file/versions')) return json([fileMaterial.version])
      if (url.endsWith('/materials/material-file')) return json(fileMaterial)
      if (url.endsWith('/files/file-photo/analysis/retry')) return json({ status: 'PENDING', attemptCount: 0, nextAttemptTime: '2026-08-22T15:00:00Z' })
      if (url.endsWith('/files/file-note/download-url')) return json({ downloadUrl: '/api/v1/local-storage/files/file-note', expiresAt: '2026-08-22T15:00:00Z' })
      return json({}, 404)
    })
    const user = userEvent.setup()
    renderRoute('/materials/material-file?highlightFile=file-photo')

    const photo = await screen.findByRole('article', { name: /street.jpg/i })
    expect(photo).toHaveClass('file-card--matched')
    expect(within(photo).getByText('A flooded street beside brick buildings.')).toBeInTheDocument()
    expect(within(photo).getByText('flood')).toHaveClass('tag--ai')
    expect(screen.getByText('field')).toHaveClass('tag--manual')
    expect(within(photo).getByRole('button', { name: /retry ai analysis/i })).toBeInTheDocument()
    expect(screen.queryByText(/ai analysis/i, { selector: '[aria-label="notes.txt"] *' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /download notes.txt/i }))
    await waitFor(() => expect(api).toHaveBeenCalledWith(
      '/api/v1/files/file-note/download-url',
      expect.objectContaining({ method: 'POST' }),
    ))
  })

  it('allows a non-owner to download but never exposes AI retry', async () => {
    installApi({ material: { ...fileMaterial, canEdit: false } })
    renderRoute('/materials/material-file')

    const photo = await screen.findByRole('article', { name: /street.jpg/i })
    expect(within(photo).queryByRole('button', { name: /retry ai analysis/i })).not.toBeInTheDocument()
    expect(within(photo).getByRole('button', { name: /download street.jpg/i })).toBeInTheDocument()
  })
})
