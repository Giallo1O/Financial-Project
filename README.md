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
  - Pubblica su Kafka gli eventi di creazione/cancellazione transazione e di cancellazione categoria.
- **`financial-manager-dashboard`** (Spring Boot, porta 8081) — consuma gli eventi Kafka e mantiene riepiloghi mensili entrate/uscite (`category_summaries`); espone `GET /api/dashboard/stats`.
- **`financial-manager-frontend`** (React 19 + Vite) — SPA servita da Nginx, che in Kubernetes fa anche da reverse proxy (`/api/transaction`, `/api/dashboard`, `/api/user`).

### Topic Kafka

| Topic | Produttore | Consumatore |
|---|---|---|
| `transactions-topic` | transaction | dashboard |
| `transactions-delete-topic` | transaction | dashboard |
| `category-delete-topic` | transaction | — (vedi "Stato del progetto") |
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

Registra un utente dalla schermata di login e parti dalle **Categorie**, poi inserisci le prime transazioni (oppure importa un CSV).

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

1. Modifica `SERVER_USER` / `SERVER_IP` in `deploy.sh`.
2. Lancia `./deploy.sh`: costruisce le 4 immagini (`finmanager/*:latest`), le salva in `.tar`, le copia via `scp` sul server, le importa (Docker; ci sono le righe già pronte per k3s/MicroK8s) e applica `k8s/finmanager.yaml`.
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

- ✅ I tre servizi Spring Boot **compilano** (Java 21).
- ✅ Il frontend **builda** (`vite build`; avviso: bundle > 500 kB, manca il code-splitting).
- ⚠️ `npm run lint`: **10 errori, 4 warning** (soprattutto variabili inutilizzate).
- ⚠️ **Test:** esiste solo il test di avvio di default (`contextLoads`) in transaction e dashboard; nessun test sulla logica.

### Problemi noti e cose da sistemare

Ordinati per importanza.

1. **Sicurezza — le API non verificano l'identità.** Il login genera un token, ma nessun servizio lo controlla: il `userId` arriva come parametro e chiunque conosca l'UUID di un altro utente può leggere o modificare i suoi dati. Anche `PUT`/`DELETE` su categorie, obiettivi e ricorrenti non controllano che la risorsa appartenga all'utente. Serve validare il token (o passare a JWT) e filtrare per utente lato server.
2. **Dashboard service: bug nel calcolo entrata/uscita.** In `DashboardService` si confronta l'enum con una stringa (`event.type().equals("USCITA")`), che è sempre `false`: tutto viene contato come "entrata". Oggi non si vede perché la dashboard del frontend **non usa** `/api/dashboard/stats` ma ricalcola tutto dalle transazioni. Il servizio dashboard e Kafka sono quindi di fatto non sfruttati dalla UI: o si corregge e li si usa, o si decide di eliminarli.
3. **Evento di cancellazione categoria non gestito.** Il servizio transaction pubblica su `category-delete-topic`, ma il dashboard non lo ascolta (esiste solo il DTO) e il topic non è dichiarato in `KafkaTopicConfig`. Inoltre eliminando una categoria le transazioni collegate restano con un `categoryId` orfano.
4. **Cancellazione transazioni:** `deleteTransaction` fa la delete su DB e poi invia l'evento Kafka senza transazione/outbox; se Kafka è giù i due stati si disallineano (stesso discorso per la creazione).
5. **Segreti e configurazione hardcoded:** password DB (`user`/`password`), pgAdmin `admin/admin`, IP e utente `root` in `deploy.sh`, CORS fissato su `http://localhost:3000` (nel servizio user è `*`). Da spostare in variabili d'ambiente / Kubernetes Secrets.
6. **Ricorrenze solo mensili:** il campo `recurrenceInterval` esiste ma la logica supporta solo "ogni mese al giorno X".
7. **Kubernetes:** immagini `:latest` con `IfNotPresent`, nessun Ingress/TLS, nessuna probe di salute; `kafka:latest` e `ddl-auto=update` non sono adatti alla produzione (meglio Flyway/Liquibase).
8. **Pulizia:** `financial-manager-user/.factorypath` (file IDE) versionato, `README.md` del frontend ancora quello del template Vite (ora sostituibile con un rimando a questo file), file `application.properties.clean` accanto alle properties vere, log Kafka a livello DEBUG nel dashboard.

### Prossimi passi consigliati

1. Autenticazione reale sulle API (punto 1).
2. Decidere il destino del dashboard-service (correggere il bug e usarlo, oppure rimuoverlo) e gestire la cancellazione categoria (punti 2–3).
3. Aggiungere test sulla logica chiave (ricorrenze, import CSV, obiettivi) e sistemare il lint.
4. Spostare segreti/config fuori dal codice e rendere il deploy K8s più robusto.
