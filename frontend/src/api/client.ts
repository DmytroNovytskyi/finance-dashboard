import type { ApiError } from '../types'

/** Base path of the REST API, reverse-proxied to the backend in dev and in prod. */
export const API_BASE = '/api/v1'

/** Thrown when the API answers with a non-2xx status; carries the {@link ApiError} body. */
export class ApiRequestError extends Error {
  readonly status: number
  readonly path: string

  constructor(error: ApiError, cause?: unknown) {
    super(error.message || error.error || `Request failed (${error.status})`, { cause })
    this.name = 'ApiRequestError'
    this.status = error.status
    this.path = error.path
  }
}

/** Builds a query string, dropping null/undefined/empty values. */
export function buildQuery(params: object): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined) continue
    const items = Array.isArray(value) ? value : [value]
    for (const item of items) {
      if (item === null || item === undefined || item === '') continue
      search.append(key, String(item))
    }
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

type RequestInitLike = {
  method?: string
  body?: BodyInit | null
  headers?: Record<string, string>
}

async function parseError(res: Response): Promise<ApiError> {
  let body: Partial<ApiError> | null = null
  try {
    body = (await res.json()) as Partial<ApiError>
  } catch {
    body = null
  }
  return {
    timestamp: body?.timestamp ?? new Date().toISOString(),
    status: body?.status ?? res.status,
    error: body?.error ?? res.statusText,
    message: body?.message ?? res.statusText,
    path: body?.path ?? res.url,
  }
}

/** Performs a JSON request against the API and decodes the typed response body. */
export async function request<T>(path: string, init?: RequestInitLike): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
    ...init?.headers,
  }
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (!res.ok) {
    throw new ApiRequestError(await parseError(res))
  }
  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}
