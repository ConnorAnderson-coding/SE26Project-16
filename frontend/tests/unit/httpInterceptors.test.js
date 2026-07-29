import { beforeEach, describe, expect, it, vi } from 'vitest'

const captured = vi.hoisted(() => ({
  request: null,
  responseSuccess: null,
  responseFailure: null
}))

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: {
          use: vi.fn(handler => {
            captured.request = handler
          })
        },
        response: {
          use: vi.fn((success, failure) => {
            captured.responseSuccess = success
            captured.responseFailure = failure
          })
        }
      }
    }))
  }
}))

import {
  getStoredUser,
  getToken,
  setStoredUser,
  setToken
} from '../../src/services/http'

beforeEach(() => {
  localStorage.clear()
  window.history.replaceState({}, '', '/')
})

describe('HTTP storage and interceptors', () => {
  it('adds an authorization header only when a token exists', () => {
    const plain = { headers: {} }
    expect(captured.request(plain)).toBe(plain)
    setToken('token-1')
    const authorized = captured.request({ headers: {} })
    expect(authorized.headers.Authorization).toBe('Bearer token-1')
    expect(getToken()).toBe('token-1')
    setToken(null)
    expect(getToken()).toBeNull()
  })

  it('unwraps API envelopes, plain responses, and business errors', async () => {
    expect(captured.responseSuccess({ data: { code: 0, data: { id: 1 } } })).toEqual({ id: 1 })
    expect(captured.responseSuccess({ data: ['plain'] })).toEqual(['plain'])
    await expect(captured.responseSuccess({ data: { code: 400, message: 'bad request' } }))
      .rejects.toMatchObject({ message: 'bad request', code: 400 })
    await expect(captured.responseSuccess({ data: { code: 500 } }))
      .rejects.toBeInstanceOf(Error)
  })

  it('normalizes transport errors and clears unauthorized sessions', async () => {
    setToken('token')
    setStoredUser({ id: 'user-1' })
    await expect(captured.responseFailure({
      response: { status: 401, data: { message: 'unauthorized' } }
    })).rejects.toMatchObject({ message: 'unauthorized', status: 401 })
    expect(getToken()).toBeNull()
    expect(getStoredUser()).toBeNull()

    await expect(captured.responseFailure({ message: 'network down' }))
      .rejects.toMatchObject({ message: 'network down' })
    await expect(captured.responseFailure({}))
      .rejects.toBeInstanceOf(Error)
  })

  it('stores, removes, and safely parses user data', () => {
    setStoredUser({ id: 'user-1' })
    expect(getStoredUser()).toEqual({ id: 'user-1' })
    setStoredUser(null)
    expect(getStoredUser()).toBeNull()
    localStorage.setItem('campus-activity-user', '{bad json')
    expect(getStoredUser()).toBeNull()
  })
})
