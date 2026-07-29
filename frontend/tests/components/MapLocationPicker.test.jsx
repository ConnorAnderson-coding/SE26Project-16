import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
  delete window.AMap
})

describe('MapLocationPicker', () => {
  it('shows a configuration notice when the map key is absent', async () => {
    vi.stubEnv('VITE_AMAP_KEY', '')
    const { default: MapLocationPicker } = await import('../../src/components/MapLocationPicker')
    render(<MapLocationPicker />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
  }, 15000)

  it('creates a map, reports clicks, updates coordinates, and destroys the map', async () => {
    vi.stubEnv('VITE_AMAP_KEY', 'test-key')
    const mapOn = vi.fn()
    const markerOn = vi.fn()
    const map = {
      add: vi.fn(),
      on: mapOn,
      setCenter: vi.fn(),
      destroy: vi.fn()
    }
    const marker = {
      on: markerOn,
      setPosition: vi.fn()
    }
    window.AMap = {
      Map: vi.fn(function Map() { return map }),
      Marker: vi.fn(function Marker() { return marker })
    }
    const onChange = vi.fn()
    const { default: MapLocationPicker } = await import('../../src/components/MapLocationPicker')
    const { rerender, unmount } = render(
      <MapLocationPicker value={{ latitude: 31.1, longitude: 121.1 }} onChange={onChange} />
    )

    await waitFor(() => expect(window.AMap.Map).toHaveBeenCalled())
    const clickHandler = mapOn.mock.calls.find(([name]) => name === 'click')[1]
    clickHandler({
      lnglat: {
        getLng: () => 121.1234567,
        getLat: () => 31.7654321
      }
    })
    expect(onChange).toHaveBeenCalledWith({ latitude: 31.765432, longitude: 121.123457 })

    rerender(<MapLocationPicker value={{ latitude: 31.2, longitude: 121.2 }} onChange={onChange} />)
    expect(marker.setPosition).toHaveBeenCalledWith([121.2, 31.2])
    expect(map.setCenter).toHaveBeenCalledWith([121.2, 31.2])

    unmount()
    expect(map.destroy).toHaveBeenCalled()
  }, 15000)

  it('loads the map SDK script when AMap is not already present', async () => {
    vi.stubEnv('VITE_AMAP_KEY', 'script-key')
    vi.stubEnv('VITE_AMAP_SECURITY_CODE', 'security-code')
    const map = { add: vi.fn(), on: vi.fn(), destroy: vi.fn() }
    const marker = { on: vi.fn(), setPosition: vi.fn() }
    const append = vi.spyOn(document.head, 'appendChild').mockImplementation((script) => {
      window.AMap = {
        Map: function Map() { return map },
        Marker: function Marker() { return marker }
      }
      queueMicrotask(() => script.onload())
      return script
    })
    const { default: MapLocationPicker } = await import('../../src/components/MapLocationPicker')
    const { unmount } = render(<MapLocationPicker />)
    await waitFor(() => expect(append).toHaveBeenCalled())
    expect(window._AMapSecurityConfig).toEqual({ securityJsCode: 'security-code' })
    expect(append.mock.calls[0][0].src).toContain('script-key')
    unmount()
  }, 15000)
})
