import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import './mobile.css' // dopo gli stili dei componenti: sovrascrive quelli da computer sugli schermi piccoli
import { PrivacyProvider } from './context/PrivacyContext.jsx'

// App installabile: il service worker richiede HTTPS (o localhost); su http:// semplice non si registra e l'app funziona lo stesso
if ('serviceWorker' in navigator && window.isSecureContext && import.meta.env.PROD) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => { }));
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <PrivacyProvider>
      <App />
    </PrivacyProvider>
  </StrictMode>,
)

