import { useEffect, useRef, useState } from 'react'
import { Alert, Spin } from 'antd'

const AMAP_KEY = import.meta.env.VITE_AMAP_KEY
const AMAP_SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_CODE
const DEFAULT_CENTER = { latitude: 31.0252, longitude: 121.4337 }

let amapLoader

function loadAmap() {
  if (!AMAP_KEY) {
    return Promise.reject(new Error('missing-key'))
  }
  if (window.AMap) {
    return Promise.resolve(window.AMap)
  }
  if (amapLoader) {
    return amapLoader
  }
  if (AMAP_SECURITY_CODE) {
    window._AMapSecurityConfig = {
      securityJsCode: AMAP_SECURITY_CODE
    }
  }
  amapLoader = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(AMAP_KEY)}`
    script.async = true
    script.onload = () => resolve(window.AMap)
    script.onerror = () => reject(new Error('load-failed'))
    document.head.appendChild(script)
  })
  return amapLoader
}

export default function MapLocationPicker({ value, onChange }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)
  const [status, setStatus] = useState(AMAP_KEY ? 'loading' : 'missing-key')

  useEffect(() => {
    let disposed = false

    loadAmap()
      .then((AMap) => {
        if (disposed || !containerRef.current) return
        const center = [
          value?.longitude ?? DEFAULT_CENTER.longitude,
          value?.latitude ?? DEFAULT_CENTER.latitude
        ]
        const map = new AMap.Map(containerRef.current, {
          zoom: 16,
          center
        })
        const marker = new AMap.Marker({
          position: center,
          draggable: true
        })
        map.add(marker)

        const updatePoint = (lnglat) => {
          const longitude = Number(lnglat.getLng().toFixed(6))
          const latitude = Number(lnglat.getLat().toFixed(6))
          marker.setPosition([longitude, latitude])
          onChange?.({ latitude, longitude })
        }

        map.on('click', event => updatePoint(event.lnglat))
        marker.on('dragend', event => updatePoint(event.lnglat))
        mapRef.current = map
        markerRef.current = marker
        setStatus('ready')
      })
      .catch(() => {
        if (!disposed) setStatus(AMAP_KEY ? 'failed' : 'missing-key')
      })

    return () => {
      disposed = true
      if (mapRef.current) {
        mapRef.current.destroy()
        mapRef.current = null
        markerRef.current = null
      }
    }
  }, [])

  useEffect(() => {
    if (!mapRef.current || !markerRef.current || value?.latitude == null || value?.longitude == null) {
      return
    }
    const position = [value.longitude, value.latitude]
    markerRef.current.setPosition(position)
    mapRef.current.setCenter(position)
  }, [value?.latitude, value?.longitude])

  if (status === 'missing-key') {
    return <Alert type="info" showIcon message="未配置高德地图 Key，可手动填写经纬度" />
  }

  if (status === 'failed') {
    return <Alert type="warning" showIcon message="地图加载失败，可手动填写经纬度" />
  }

  return (
    <div style={{ position: 'relative' }}>
      {status === 'loading' && (
        <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', zIndex: 1 }}>
          <Spin />
        </div>
      )}
      <div ref={containerRef} style={{ height: 320, width: '100%', borderRadius: 6, overflow: 'hidden' }} />
    </div>
  )
}
