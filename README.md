# FinManager — Gestione Finanze Personali

Applicazione web per tenere traccia di entrate e uscite, budget, obiettivi di risparmio e spese ricorrenti. Interfaccia in italiano, architettura a **microservizi** con **Kafka** per la comunicazione asincrona, pensata per girare sia in locale (Docker Compose) sia su un cluster **Kubernetes**.

## Funzionalità

| Area | Cosa fa |
|---|---|
| **Account** | Registrazione e login con username/password (hash BCrypt). Ogni utente vede solo i propri dati. |
| **Dashboard** | Panoramica del mese corrente: saldo, entrate/uscite, grafici a torta separati per categorie di entrata e di uscita, ultime transazioni. |
| **Transazioni** | Inserimento, elenco e cancellazione di entrate/uscite con data, categoria e descrizione. Esportazione in **Excel** e **PDF**. |
| **Import CSV** | Wizard in 3 step: carica il CSV, correggi le righe (data, importo, categoria), conferma. L'import avviene in modo asincrono via Kafka. Riconosce date `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy` e importi con virgola o punto. |
| **Categorie** | Colore, icona, tipo (entrata/uscita), budget mensile, flag **fissa/variabile** e **tag** liberi (es. `#Parigi2026`). |
| **Budget** | Per ogni categoria di spesa: budget mensile vs speso, con percentuale e avviso di sforamento. |
| **Analisi** | Grafici del mese selezionato: barre per categoria (con filtro entrate/uscite) e andamento giornaliero per categoria. |
| **Confronti** | 4 schede: *mese su mese*, *trend* degli ultimi N mesi, *analisi per tag*, *spese fisse vs variabili* (con regola del 50/30/20). |
| **Ricorrenti & Scadenze** | Spese/entrate periodiche (affitto, abbonamenti…) con **calendario delle scadenze**. Si confermano a mano oppure in automatico (`autoConfirm`): un job giornaliero alle 01:00 genera le transazioni in scadenza. |
| **Obiettivi di risparmio** | "Salvadanai" con importo target, data obiettivo, colore/icona; versamenti e prelievi; barra di avanzamento; completamento automatico. |
| **Privacy mode** | Pulsante "occhio" nella sidebar che sfoca tutte le cifre. |

## Architettura

```
                         ┌───────────────────────┐
   Browser ───────────►  │  Frontend (React/Nginx) │  :3000 (compose) / :30000 (k8s)
                         └──────┬────────┬────────┘
              ┌─────────────────┘        │         └──────────────┐
              ▼                          ▼                        ▼
   ┌────────────────────┐   ┌────────────────────┐   ┌────────────────────┐
   │ user-service :8082 │   │ transaction-service│   │ dashboard-service  │
   │ auth               │   │ :8080              │   │ :8081              │
   └─────────┬──────────┘   └───┬────────────▲───┘   └───▲────────────────┘
             ▼                  ▼            │           │
        Postgres users     Postgres      Kafka ──────────┘
        (db_users)         transactions  (eventi)    Postgres dashboard
                           (db_transactions)         (db_dashboard)
```

Un database PostgreSQL per ogni servizio (nessun database condiviso).

### Servizi

- **`financial-manager-user`** (Spring Boot, porta 8082) — `POST /api/auth/register`, `POST /api/auth/login`. Restituisce `{token, username, userId}`.
- **`financial-manager-transaction`** (Spring Boot, porta 8080) — il cuore del sistema:
  - `/api/transactions` (CRUD, filtro `month`/`year`, `parse-csv`, `bulk-import`)
  - `/api/categories` (CRUD, budget, tag, fissa/variabile)
  - `/api/recurring-transactions` (CRUD, `confirm`, `process-auto`, job schedulato)
  - `/api/savings-goals` (CRUD, `deposit`, `withdraw`)
  - Pubblica su Kafka gli eventi di creazione/cancellazione transazione (dopo il commit sul DB).
  - Tutte le API richiedono `Authorization: Bearer <token>`; i dati sono sempre limitati all'utente del token.
- **`financial-manager-dashboard`** (Spring Boot, porta 8081) — consuma gli eventi Kafka e mantiene riepiloghi mensili entrate/uscite (`category_summaries`); espone `GET /api/dashboard/stats`.
- **`financial-manager-frontend`** (React 19 + Vite) — SPA servita da Nginx, che in Kubernetes fa anche da reverse proxy (`/api/transaction`, `/api/dashboard`, `/api/user`).

### Topic Kafka

| Topic | Produttore | Consumatore |
|---|---|---|
| `transactions-topic` | transaction | dashboard |
| `transactions-delete-topic` | transaction | dashboard |
| `transaction.import.requests` | transaction (bulk import) | transaction (crea le transazioni) |

### Stack

- **Backend:** Java 21 (virtual threads), Spring Boot 4.0.3, Spring Data JPA, Spring Kafka, PostgreSQL 16, OpenCSV, Lombok
- **Frontend:** React 19, Vite 7, React Router 7, Recharts, react-datepicker + date-fns (locale IT), lucide-react, `xlsx`, `jspdf` + `jspdf-autotable`
- **Infra:** Docker, Docker Compose, Kubernetes (manifest unico), Kafka in modalità KRaft

## Avvio in locale (Docker Compose)

Requisiti: Docker con Compose.

```bash
docker compose up --build
```

| Cosa | URL |
|---|---|
| App | http://localhost:3000 |
| transaction / dashboard / user API | http://localhost:8080 / 8081 / 8082 |
| Kafka UI | http://localhost:8090 |
| pgAdmin | http://localhost:5050 (`admin@admin.com` / `admin`) |

Le password di database e pgAdmin si cambiano copiando `.env.example` in `.env` (default: quelle di sempre). Registra un utente dalla schermata di login e parti dalle **Categorie**, poi inserisci le prime transazioni (oppure importa un CSV).

### Sviluppo senza Docker per i servizi

```bash
# infrastruttura (db + kafka) dal compose
docker compose up postgres-transactions postgres-dashboard postgres-users kafka

# backend (in ogni cartella financial-manager-*)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/db_transactions \
SPRING_DATASOURCE_USERNAME=user SPRING_DATASOURCE_PASSWORD=password \
./mvnw spring-boot:run

# frontend
cd financial-manager-frontend && npm ci && npm run dev
```

Porte DB in locale: transactions `5432`, dashboard `5433`, users `5434`. Il servizio `user` ha già dei default per il suo database; `transaction` e `dashboard` richiedono le variabili `SPRING_DATASOURCE_*`.

## Deploy su Kubernetes

1. Imposta `SERVER_USER` / `SERVER_IP` (variabili d'ambiente, oppure modifica i default in `deploy.sh`).
2. Lancia `DB_PASSWORD=... ./deploy.sh`: costruisce le 4 immagini con un tag unico (`finmanager/*:<timestamp>`), le salva in `.tar`, le copia via `scp` sul server, le importa (Docker; ci sono le righe già pronte per k3s/MicroK8s), crea il Secret `finmanager-db` se manca e applica `k8s/finmanager.yaml`.
   - `DB_PASSWORD` serve solo la prima volta (quando il Secret non esiste). **Se i database esistono già, usa la password attuale (`password`)**: Postgres applica la password solo alla creazione del volume.
   - I pod hanno probe di startup/readiness/liveness (`/actuator/health/...`).
3. L'app è esposta con un `NodePort` su **`:30000`** nel namespace `finmanager`.

Il frontend per K8s viene buildato con URL relativi (`/api/...`) che Nginx inoltra ai servizi interni.

## Struttura del repository

```
.
├── docker-compose.yml            # stack locale completo
├── deploy.sh                     # build + deploy su Kubernetes via SSH
├── k8s/finmanager.yaml           # namespace, PVC, db, kafka, servizi, frontend
├── TODO                          # idee funzionalità (tutte implementate, vedi sotto)
├── financial-manager-user/
├── financial-manager-transaction/
├── financial-manager-dashboard/
└── financial-manager-frontend/
```

---

## Stato del progetto

*Aggiornato al 21/10/2026, su `main` (commit `119441b`).*

### Roadmap (file `TODO`)

| Idea | Stato |
|---|---|
| Transazioni ricorrenti + calendario scadenze | ✅ fatto |
| Confronto mese su mese | ✅ fatto |
| Spese fisse vs variabili | ✅ fatto |
| Obiettivi di risparmio | ✅ fatto |
| Export Excel/PDF + privacy mode | ✅ fatto |
| Tag oltre le categorie | ✅ fatto |
| Budget mensili per categoria *(aggiunto in corsa)* | ✅ fatto |

Tutta la roadmap è completata. Cronologia: avvio il 09/03/2026, funzionalità principali a marzo, deploy Kubernetes e ultime feature a settembre 2026 (49 commit).

### Branch

Tutti i branch sono **già dentro `main`** (0 commit non uniti):

| Branch | Contenuto | Stato |
|---|---|---|
| `main` | Tutto il progetto | aggiornato |
| `dev` | Ricorrenti, obiettivi, fisse/variabili, privacy/export + K8s | unito (PR #1) |
| `feature/category-remover` | Cancellazione categorie, flag entrata/uscita | unito, obsoleto |
| `feature/refactor-analytics` | Budget mensili, date picker italiano | unito, obsoleto |
| `feature/new-insight` | Pagina Confronti, tag, nuovo tema colori | unito, obsoleto |
| `claude/blissful-pascal-cpvlet` | Branch di lavoro di questa sessione | = `main` |

I tre `feature/*` si possono cancellare senza perdere nulla.

### Verifiche fatte

- ✅ I tre servizi Spring Boot **compilano** (Java 21) e il frontend **builda**.
- ✅ `npm run lint`: 0 errori (restano 4 warning sulle dipendenze degli hook React).
- ✅ **Test** (unitari, senza database): token/CORS (`AuthInterceptor`), import CSV, ricorrenze (data successiva a fine mese, accesso di altri utenti), obiettivi di risparmio, e il calcolo entrate/uscite del dashboard. Restano solo i `contextLoads` di default, che richiedono un database.
- ⚠️ Non provato end-to-end: nel mio ambiente non c'erano Postgres/Kafka, quindi stack completo, Docker e Kubernetes non sono stati avviati.

### Problemi risolti

1. **Sicurezza:** ogni richiesta a transaction e dashboard viene validata chiedendo a `user-service` (`GET /api/auth/me`) di chi è il token; il `userId` inviato dal client è ignorato. Lettura, modifica e cancellazione di transazioni, categorie, ricorrenze e obiettivi sono limitate al proprietario (404 altrimenti); nei POST gli `id` del client sono ignorati.
2. **Dashboard-service:** corretto il calcolo entrata/uscita (confrontava l'enum con una stringa, quindi tutto finiva tra le entrate). **I riepiloghi già salvati nel DB del dashboard sono sbagliati**: vanno ricostruiti svuotando `category_summaries` e azzerando il consumer group (es. cambiando `dashboard-group-v2`) per rileggere gli eventi.
3. **Cancellazione categoria:** le transazioni e le ricorrenze collegate restano ma senza categoria (mostrate con "—"); i totali non cambiano. Rimosso l'evento Kafka `category-delete-topic`, che non aveva consumatori.
4. **Eventi Kafka:** ora partono solo dopo il commit del DB (niente eventi per operazioni annullate).
5. **Configurazione:** password DB e pgAdmin da `.env` (compose) e da un Secret Kubernetes; CORS configurabile con `CORS_ALLOWED_ORIGINS` (prima: fisso su localhost:3000, e `*` nel servizio user); IP/utente del server sovrascrivibili.
6. **Kubernetes:** immagini con tag unico a ogni deploy (con `:latest` fisso i pod non venivano riavviati), probe di salute, Kafka alla versione `4.0.0` invece di `latest`.
7. **Pulizia:** rimossi `.factorypath` e i file `application.properties.clean` (obsoleti), log Kafka in DEBUG, README del frontend sostituito.

### Limiti noti e cose ancora aperte

- **Token:** non scade mai ed è salvato in chiaro nel DB; un nuovo login invalida il precedente (un dispositivo alla volta); `user-service` viene interrogato a ogni richiesta (nessuna cache).
- **Kafka:** se il broker è irraggiungibile al momento dell'invio l'evento si perde (servirebbe un outbox). Il dashboard-service è comunque **ancora non usato** dalla UI, che ricalcola tutto dalle transazioni.
- **Ricorrenze solo mensili:** il campo `recurrenceInterval` esiste ma la logica supporta solo "ogni mese al giorno X".
- **Kubernetes:** nessun Ingress/TLS; `ddl-auto=update` al posto di migrazioni (Flyway/Liquibase); password del DB uguale per i tre database.
- **Frontend:** bundle > 500 kB (manca il code-splitting).

### Prossimi passi

Dipendono dalla direzione del progetto: in particolare cosa fare del dashboard-service (usarlo nella UI o rimuoverlo) e se servono ricorrenze non mensili.
