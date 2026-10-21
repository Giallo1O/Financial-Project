# FinManager — frontend

SPA React 19 + Vite. La documentazione del progetto (funzionalità, architettura, avvio e deploy) è nel [README principale](../README.md).

```bash
npm ci
npm run dev    # sviluppo (http://localhost:5173)
npm run build  # build di produzione in dist/
npm run lint
```

Gli URL dei backend si configurano a build time con `VITE_TRANSACTION_API_URL` e `VITE_USER_API_URL` (default: `localhost` sulle porte 8080/8082).
