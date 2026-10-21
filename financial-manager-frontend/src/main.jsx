import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { PrivacyProvider } from './context/PrivacyContext.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <PrivacyProvider>
      <App />
    </PrivacyProvider>
  </StrictMode>,
)

