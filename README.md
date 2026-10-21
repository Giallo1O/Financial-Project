# FinManager — Gestione Finanze Personali

Applicazione web per gestire le proprie finanze su un **home server**: movimenti, budget, obiettivi di risparmio, spese ricorrenti e **scontrini della spesa letti da un'AI**. Interfaccia in italiano, pensata per poche persone (account separati) sulla rete di casa. Gira con Docker Compose o su Kubernetes.

## Funzionalità

| Area | Cosa fa |
|---|---|
| **Account** | Registrazione e login (password con hash BCrypt). Ogni utente vede solo i propri dati. |
| **Dashboard** | Mese corrente: saldo, entrate/uscite, grafici a torta per categoria, ultime transazioni. |
| **Transazioni** | Inserimento, elenco e cancellazione di entrate/uscite. Esportazione in **Excel** e **PDF**. |
| **Import CSV** | Wizard in 3 step: carica il CSV, controlla e correggi le righe, conferma. **Salta i movimenti già presenti** (stessa data, importo, tipo e descrizione): puoi ricaricare estratti conto che si sovrappongono senza creare doppioni. Riconosce date `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy` e importi con virgola o punto. |
| **Spesa (scontrini)** | Carichi la foto (o il PDF) di uno scontrino: un'AI (Claude) estrae negozio, data, totale e ogni articolo con nome normalizzato, quantità, prezzo e tipo di alimento. Controlli e correggi la bozza, poi salvi: viene creata la **spesa** tra le transazioni e le righe prodotto. Pagine: elenco scontrini, **prodotti e prezzi** (minimo/medio/massimo, dove costa meno, andamento nel tempo), **spesa per tipo di alimento** per mese. |
| **Categorie** | Colore, icona, tipo (entrata/uscita), budget mensile, flag **fissa/variabile** e **tag** (es. `#Parigi2026`). |
| **Budget** | Per ogni categoria di spesa: budget mensile vs speso, con avviso di sforamento. |
| **Analisi** | Grafici del mese: barre per categoria e andamento giornaliero. |
| **Confronti** | *Mese su mese*, *trend* degli ultimi N mesi, *analisi per tag*, *spese fisse vs variabili* (regola 50/30/20). |
| **Ricorrenti & Scadenze** | Spese/entrate periodiche con **calendario delle scadenze**; conferma manuale o automatica (job alle 01:00). Solo cadenza mensile. |
| **Obiettivi di risparmio** | "Salvadanai" con importo target, data, versamenti e prelievi, barra di avanzamento. |
| **Privacy mode** | Pulsante "occhio" che sfoca tutte le cifre. |
| **Backup** | Dump giornaliero compresso di entrambi i database (ultimi 14 giorni). |

## Architettura

```
                       ┌─────────────────────────┐
   Browser ─────────►  │ Frontend (React + Nginx) │  :3000 (compose) / :30000 (k8s)
                       └─────┬──────────────┬─────┘
                             ▼              ▼
              ┌──────────────────────┐  ┌──────────────────┐
              │ transaction-service  │  │ user-service     │
              │ :8080                │─►│ :8082            │  valida il token (GET /api/auth/me)
              └───────┬──────────────┘  └────────┬─────────┘
                      │                          ▼
                      ▼                     Postgres users
                 Postgres transactions
                      │
                      └──► API Anthropic (solo per leggere gli scontrini)
```

Un database PostgreSQL per servizio. Nessun broker di messaggi.

### Servizi

- **`financial-manager-user`** (Spring Boot, :8082) — `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` (risolve un token in utente, usato dagli altri servizi).
- **`financial-manager-transaction`** (Spring Boot, :8080) — il cuore del sistema. Tutte le API richiedono `Authorization: Bearer <token>` e lavorano solo sui dati dell'utente del token:
  - `/api/transactions` (CRUD, filtro `month`/`year`, `parse-csv` con anteprima doppioni, `bulk-import`)
  - `/api/categories`, `/api/recurring-transactions`, `/api/savings-goals`
  - `/api/receipts` (`scan`, salvataggio, elenco, dettaglio, cancellazione, `products`, `products/history`, `spending`)
- **`financial-manager-frontend`** (React 19 + Vite) — SPA servita da Nginx, che in Kubernetes fa anche da reverse proxy (`/api/transaction`, `/api/user`).

### Stack

- **Backend:** Java 21 (virtual threads), Spring Boot 4.0.3, Spring Data JPA, PostgreSQL 16, OpenCSV, SDK Java Anthropic, Lombok
- **Frontend:** React 19, Vite 7, React Router 7, Recharts, react-datepicker + date-fns, lucide-react, `xlsx`, `jspdf`
- **Infra:** Docker, Docker Compose, Kubernetes (manifest unico)

## Lettura degli scontrini

1. Crea una chiave API su https://console.anthropic.com e mettila in `.env` come `ANTHROPIC_API_KEY` (vedi `.env.example`). Il server deve poter raggiungere `api.anthropic.com`; senza chiave l'app funziona, solo la lettura scontrini è disattivata.
2. Vai su **Spesa → Nuovo scontrino** e carica una o più foto (fino a 4, per scontrini lunghi) o un PDF. Il browser ridimensiona le foto e applica la rotazione prima dell'invio.
3. Controlla la bozza: l'app avvisa se la somma delle righe non coincide con il totale. Gli sconti sono righe negative. Salvando si crea la transazione di spesa (categoria a scelta) e lo stesso scontrino non si può salvare due volte.

Il modello è `claude-opus-5-5` per default (`ANTHROPIC_MODEL` per cambiarlo, es. `claude-sonnet-5-5` o `claude-haiku-5-5` per costare meno: conviene confrontarli su scontrini reali). Il costo per scontrino non è ancora stato misurato su scontrini veri.

## Avvio in locale (Docker Compose)

```bash
cp .env.example .env     # poi modifica password e chiave Anthropic
docker compose up --build
```

| Cosa | URL |
|---|---|
| App | http://localhost:3000 |
| transaction / user API | http://localhost:8080 / 8082 |
| pgAdmin | http://localhost:5050 (`admin@admin.com` / password `PGADMIN_PASSWORD`) |

Registra un utente dal login, crea qualche **Categoria** e inserisci movimenti, importa un CSV o carica uno scontrino. Per far usare l'app a un'altra persona basta che si registri con un altro username: i dati sono separati.

> `DB_PASSWORD` vale solo alla **prima** creazione dei volumi Postgres; con volumi esistenti la password del DB non cambia.

### Sviluppo senza Docker per i servizi

```bash
docker compose up postgres-transactions postgres-users          # solo i database

# in financial-manager-user e financial-manager-transaction
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/db_transactions \
SPRING_DATASOURCE_USERNAME=user SPRING_DATASOURCE_PASSWORD=password \
./mvnw spring-boot:run

cd financial-manager-frontend && npm ci && npm run dev
```

Porte dei DB in locale: transactions `5432`, users `5434`. Il servizio `user` ha dei default per il suo database. Se Docker non è disponibile, `SPRING_DOCKER_COMPOSE_ENABLED=false` disattiva l'avvio automatico del compose da parte di Spring.

## Backup e ripristino

Un servizio `backup` (compose) o un CronJob (Kubernetes, ore 03:00) crea ogni giorno `transactions-<data>.sql.gz` e `users-<data>.sql.gz` e tiene gli ultimi 14 giorni: in `./backups` con compose, nel volume `backups-pvc` su Kubernetes.

**Sono sullo stesso disco del server:** se il disco si rompe, si perdono anche i backup. Copia ogni tanto la cartella su un altro supporto (NAS, disco esterno, altro PC).

Ripristino su database vuoto:

```bash
gunzip -c backups/transactions-AAAAMMGG-HHMMSS.sql.gz | docker exec -i pg-trans psql -U user db_transactions
```

## Deploy su Kubernetes

1. Imposta `SERVER_USER` / `SERVER_IP` (variabili d'ambiente, oppure modifica i default in `deploy.sh`).
2. Lancia `DB_PASSWORD=... ANTHROPIC_API_KEY=... ./deploy.sh`: costruisce le immagini con un tag unico, le copia sul server, crea i Secret (`finmanager-db`, `finmanager-anthropic`), rimuove i vecchi dashboard-service/Kafka se presenti e applica `k8s/finmanager.yaml`.
   - `DB_PASSWORD` serve solo la prima volta. **Se i database esistono già, usa la password attuale (`password`)**: Postgres la applica solo alla creazione del volume.
   - `ANTHROPIC_API_KEY` si può omettere se il Secret esiste già.
   - Il volume del vecchio dashboard (`pg-dash-pvc`) non viene cancellato: eliminalo a mano quando vuoi.
3. L'app è su un `NodePort` **`:30000`**, namespace `finmanager`. I pod hanno probe di salute (`/actuator/health/...`).

## Struttura del repository

```
.
├── docker-compose.yml            # stack locale: db, servizi, frontend, backup, pgAdmin
├── deploy.sh                     # build + deploy su Kubernetes via SSH
├── k8s/finmanager.yaml           # namespace, PVC, db, servizi, frontend, CronJob di backup
├── .env.example                  # password, chiave Anthropic, CORS
├── TODO                          # idee originali (tutte realizzate)
├── financial-manager-user/
├── financial-manager-transaction/
└── financial-manager-frontend/
```

---

## Stato del progetto

*Aggiornato al 21/10/2026.*

Tutta la roadmap originale (`TODO`) è realizzata, più budget mensili, scontrini e backup. Direzione attuale: servizio personale per due persone sulla rete di casa, con tutti i movimenti caricati dai file della banca e dagli scontrini.

### Verifiche fatte

- ✅ **Provato end-to-end** con servizi e Postgres reali (non Docker/Kubernetes): login e isolamento tra due utenti, categorie, import con doppioni, cancellazione categoria, obiettivi e ricorrenze, scontrini (scan → bozza → salvataggio → prezzi → spese per tipo → cancellazione), e la pagina Spesa in un browser (Chromium).
- ✅ Test unitari (token/CORS, import CSV e doppioni, ricorrenze, obiettivi, JSON delle entità, scontrini); `npm run lint` senza errori (4 warning sulle dipendenze degli hook).
- ✅ Dump e ripristino di un database verificati.
- ⚠️ **La lettura vera con Claude non è stata provata:** nei test la risposta dell'AI era simulata (verificati la richiesta inviata e l'elaborazione della risposta, non la qualità della lettura). Va provata con scontrini reali.
- ⚠️ Non provati: `docker compose up`, `deploy.sh`, il manifest Kubernetes e il CronJob di backup (nel mio ambiente non c'erano Docker né Kubernetes).

### Limiti noti e cose aperte

- **Token:** non scade mai ed è salvato in chiaro nel DB; un nuovo login invalida il precedente (un dispositivo alla volta); `user-service` viene interrogato a ogni richiesta (nessuna cache).
- **Ricorrenze solo mensili:** il campo `recurrenceInterval` esiste ma la logica gestisce solo "ogni mese al giorno X".
- **Import:** se due acquisti identici nello stesso giorno sono davvero distinti ma già uno è presente, il secondo viene saltato (cambia la descrizione per importarlo). Il flag "già presente" dell'anteprima non si aggiorna se modifichi una riga; il controllo definitivo avviene al salvataggio.
- **Scontrini:** le foto non vengono conservate (solo i dati estratti); formato HEIC non supportato dal server (il browser di solito lo converte); il costo per scontrino va misurato.
- **Kubernetes:** nessun Ingress/TLS (non serve sulla rete di casa); `ddl-auto=update` al posto di migrazioni (Flyway/Liquibase); stessa password per i due database.
- **Frontend:** bundle > 500 kB (manca il code-splitting).
