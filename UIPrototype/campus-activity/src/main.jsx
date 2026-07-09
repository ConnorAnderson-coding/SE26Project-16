import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { AppProvider } from './context/AppContext'
import 'antd/dist/reset.css'
import './styles/global.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN}>
      <AppProvider>
        <App />
      </AppProvider>
    </ConfigProvider>
  </React.StrictMode>
)
