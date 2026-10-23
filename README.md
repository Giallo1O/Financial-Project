# FinManager — Gestione Finanze Personali

Applicazione web per gestire le proprie finanze su un **home server**: movimenti, budget, obiettivi di risparmio, spese ricorrenti e **scontrini della spesa letti da un'AI**. Interfaccia in italiano, pensata per poche persone (account separati) sulla rete di casa. Gira con Docker Compose o su Kubernetes.

## Funzionalità

| Area | Cosa fa |
|---|---|
| **Account** | Registrazione e login (password con hash BCrypt). Ogni utente vede solo i propri dati. |
| **Dashboard** | Mese corrente: saldo, entrate/uscite, grafici a torta per categoria, ultime transazioni. |
| **Transazioni** | Inserimento, elenco e cancellazione di entrate/uscite. Esportazione in **Excel** e **PDF**. |
| **Import CSV** | Wizard in 3 step: carica il CSV, controlla e correggi le righe, conferma. **Salta i movimenti già presenti** (stessa data, importo, tipo e descrizione) e le **uscite già registrate da uno scontrino** (stesso importo, data entro 3 giorni): puoi ricaricare estratti conto che si sovrappongono e non conti due volte la spesa che hai già salvato con lo scontrino. L'anteprima dice perché una riga verrà saltata. **Propone la categoria da solo** con le regole automatiche (vedi sotto). Riconosce date `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy` e importi con virgola o punto. |
| **Spesa (scontrini)** | Carichi la foto (o il PDF) di uno scontrino: un'AI (Claude) estrae negozio, data, totale e ogni articolo con nome normalizzato, quantità, prezzo e tipo di alimento. Controlli e correggi la bozza, poi salvi: viene creata la **spesa** tra le transazioni e le righe prodotto. Se hai già importato l'estratto conto, al salvataggio l'app propone di **collegare lo scontrino al movimento in banca** invece di creare una nuova spesa. Pagine: elenco scontrini, **prodotti e prezzi** (minimo/medio/massimo, dove costa meno, andamento nel tempo), **spesa per tipo di alimento** per mese. |
| **Categorie** | Colore, icona, tipo (entrata/uscita), budget mensile, flag **fissa/variabile** e **tag** (es. `#Parigi2026`). |
| **Categorie automatiche** | Regole "parola chiave → categoria" (es. `esselunga` → Spesa) applicate all'anteprima dell'import: la categoria compare già scelta, con l'indicazione della regola. Si **imparano da sole**: quando importi e scegli una categoria per una riga, con la casella "Ricorda le categorie scelte" (attiva di default) l'app crea la regola con il nome dell'esercente; se correggi una categoria suggerita, la regola viene corretta. Il pulsante "Crea dalle mie transazioni" ricava le regole dallo storico già categorizzato (parole che compaiono almeno 2 volte con la stessa categoria nell'80% dei casi). Le regole si vedono, si aggiungono e si eliminano nella pagina Categorie. Vince la regola più specifica (`amazon prime` batte `amazon`); una regola vale solo per categorie del tipo giusto (uscita/entrata); le parole contano intere (`bar` non trova `barilla`). |
| **Budget** | Per ogni categoria di spesa: budget mensile vs speso, con avviso di sforamento. |
| **Analisi** | Grafici del mese: barre per categoria e andamento giornaliero. |
| **Confronti** | *Mese su mese*, *trend* degli ultimi N mesi, *analisi per tag*, *spese fisse vs variabili* (regola 50/30/20). |
| **Ricorrenti & Scadenze** | Spese/entrate periodiche con **calendario delle scadenze**; conferma manuale o automatica (job alle 01:00). Solo cadenza mensile. |
| **Obiettivi di risparmio** | "Salvadanai" con importo target, data, versamenti e prelievi, barra di avanzamento. |
| **Privacy mode** | Pulsante "occhio" che sfoca tutte le cifre. |
| **App per il telefono** | Si installa su Android e iPhone (PWA) e si usa a schermo intero. Barra di navigazione in basso con il pulsante **fotocamera** al centro per scattare lo scontrino; layout adattato agli schermi piccoli. Vedi "Usare l'app dal telefono". |
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
  - `/api/category-rules` (elenco, crea/aggiorna, elimina, `learn-from-history`)
  - `/api/receipts` (`scan`, salvataggio, elenco, dettaglio, cancellazione, `products`, `products/history`, `spending`)
- **`financial-manager-frontend`** (React 19 + Vite) — SPA servita da Nginx, che in Kubernetes fa anche da reverse proxy (`/api/transaction`, `/api/user`).

### Stack

- **Backend:** Java 21 (virtual threads), Spring Boot 4.0.3, Spring Data JPA, PostgreSQL 16, OpenCSV, SDK Java Anthropic, Lombok
- **Frontend:** React 19, Vite 7, React Router 7, Recharts, react-datepicker + date-fns, lucide-react, `xlsx`, `jspdf`
- **Infra:** Docker, Docker Compose, Kubernetes (manifest unico)

## Lettura degli scontrini

1. Crea una chiave API su https://console.anthropic.com e mettila in `.env` come `ANTHROPIC_API_KEY` (vedi `.env.example`). Il server deve poter raggiungere `api.anthropic.com`; senza chiave l'app funziona, solo la lettura scontrini è disattivata.
2. Vai su **Spesa → Nuovo scontrino** e carica una o più foto (fino a 4, per scontrini lunghi) o un PDF. Il browser ridimensiona le foto e applica la rotazione prima dell'invio.
3. Controlla la bozza: l'app avvisa se la somma delle righe non coincide con il totale. Gli sconti sono righe negative. Se esiste già un movimento in banca con lo stesso importo e una data vicina (entro 3 giorni), il riquadro "Questo acquisto sembra già nei tuoi movimenti" lo propone: collegandolo non si crea una seconda spesa (la categoria scelta viene applicata solo se il movimento non ne ha una). Altrimenti, salvando si crea la transazione di spesa (categoria a scelta). Lo stesso scontrino non si può salvare due volte. Eliminando uno scontrino collegato, il movimento in banca resta.

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

## Usare l'app dal telefono

Con il telefono sul Wi-Fi di casa apri `http://<IP-del-server>:3000` (compose) oppure `:30000` (Kubernetes). Conviene dare un IP fisso al server (o un nome nel router), così l'indirizzo non cambia.

**Installarla**
- **Android (Chrome):** menu ⋮ → *Installa app* (se compare solo *Aggiungi a schermata Home* è una scorciatoia: vedi la tabella sotto).
- **iPhone/iPad (Safari):** pulsante Condividi → *Aggiungi alla schermata Home*. Deve essere Safari.

**Scattare uno scontrino:** pulsante fotocamera al centro della barra in basso → *Scatta una foto* apre la fotocamera. Per uno scontrino lungo premi *Aggiungi un'altra foto* (fino a 4, dall'alto in basso); *Galleria o PDF* usa file già presenti. Prima dell'invio il telefono ruota e riduce la foto (le foto HEIC dell'iPhone diventano JPEG). Poi controlli le righe, una scheda per articolo, e salvi.

**HTTPS (facoltativo):** su `http://` semplice funziona tutto tranne ciò che i telefoni riservano alle pagine sicure.

| | `http://IP:3000` | con HTTPS |
|---|---|---|
| Scatto con la fotocamera | sì | sì |
| *Aggiungi alla schermata Home* su iPhone, a schermo intero | sì | sì |
| *Installa app* su Android (icona vera, senza barra del browser) | no, solo scorciatoia | sì |
| Avvio più rapido e apertura anche senza rete (service worker) | no | sì |

Per avere HTTPS solo in casa si usa **Tailscale**: vedi la sezione "HTTPS con Tailscale" qui sotto.

**Cosa non c'è:** condivisione di una foto verso l'app dalla galleria (funzione solo Android), notifiche, uso dei dati senza rete. Il service worker non salva mai dati finanziari: le chiamate `/api` vanno sempre in rete.

## HTTPS con Tailscale

> **Bozza da rivedere e adattare:** questa parte è scritta ma **mai avviata** (nessun account Tailscale né Docker disponibili durante lo sviluppo). Cosa controllare o cambiare:
> - `docker-compose.yml`, servizio `tailscale`: nome del dispositivo (`TS_HOSTNAME=finmanager`), versione dell'immagine (`stable`), nome del profilo (`tailscale`).
> - `tailscale/serve.json`: la destinazione `http://frontend-service:80` (il frontend nella rete di compose) e la porta 443. `${TS_CERT_DOMAIN}` va lasciato così: lo sostituisce Tailscale.
> - `.env`: `TS_AUTHKEY` (solo al primo avvio).
> - Variante Kubernetes/server: la porta `30000` del comando `tailscale serve`.
> - Dopo che funziona: quali porte chiudere in `docker-compose.yml` (elenco in fondo alla sezione).
> - Impostazioni da fare a mano nell'admin console: MagicDNS, HTTPS, scadenza della chiave, come dare accesso alle altre persone (vedi sotto).

Tailscale crea una rete privata tra i tuoi dispositivi e dà al server un indirizzo `https://finmanager.<nome-rete>.ts.net` con certificato valido: nessuna porta aperta su internet e nessun certificato da installare sui telefoni. L'app si apre solo dai dispositivi collegati a Tailscale, **anche quando sei a casa** (l'app Tailscale sul telefono deve essere attiva). Non si usa Funnel, che invece pubblicherebbe l'app su internet.

**Una volta sola, nell'admin console di Tailscale** (`login.tailscale.com/admin`)
1. Crea l'account (il piano personale è gratuito; i limiti su utenti e dispositivi cambiano, guarda la pagina dei prezzi).
2. Sezione *DNS*: attiva *MagicDNS* e poi *Enable HTTPS* (i certificati).
3. *Settings → Keys → Generate auth key* e copia la chiave in `.env` come `TS_AUTHKEY` (serve solo al primo avvio).
4. Dopo il primo avvio: *Machines → finmanager → Disable key expiry*. Altrimenti, dopo qualche mese la chiave del dispositivo scade e il server esce dalla rete finché non lo riautentichi.

**Con docker compose**
```bash
docker compose --profile tailscale up -d --build
docker compose logs tailscale          # deve mostrare che è connesso
docker compose exec tailscale tailscale status
```
Il servizio `tailscale` parte solo con `--profile tailscale`, quindi l'avvio normale non cambia. Usa `tailscale/serve.json`, che inoltra HTTPS (443) al frontend. Il nome completo dell'indirizzo è nell'admin console (*Machines*). Dopo il primo avvio puoi togliere `TS_AUTHKEY` da `.env`: l'accesso resta salvato nel volume `tailscale_state`.

**Con Kubernetes (o senza compose):** installa Tailscale sul server (`tailscale up --hostname=finmanager`) e inoltra la porta dell'app:
```bash
sudo tailscale serve --bg 30000      # https://finmanager.<nome-rete>.ts.net  ->  http://127.0.0.1:30000
sudo tailscale serve reset           # per toglierlo
```

**Sui telefoni:** installa Tailscale (Play Store / App Store), accedi con lo stesso account e attivalo. Poi apri l'indirizzo `https://finmanager.<nome-rete>.ts.net`: su Android Chrome compare *Installa app*, su iPhone si usa Safari → Condividi → *Aggiungi alla schermata Home*.

**Per far usare l'app anche a un'altra persona:** può avere il suo account Tailscale gratuito e ricevere l'accesso in uno di due modi, dalla admin console: *Users → Invite* (entra nella tua rete) oppure *Machines → finmanager → Share* (vede solo il server; accetta il link con il suo account Tailscale). Dentro l'app ognuno si registra con il proprio utente e i dati restano separati.

**Se qualcosa non va**
- Nei log compare un errore sui certificati o su HTTPS: manca il punto 2.
- L'indirizzo non si apre dal telefono: l'app Tailscale sul telefono è spenta.
- Il server non è più raggiungibile dopo mesi: chiave scaduta, punto 4 (rigenera `TS_AUTHKEY` e riavvia il servizio).
- Il nome include il nome della tua rete (`.ts.net`) e compare nei registri pubblici dei certificati: non contiene dati dell'app, ma non è segreto.

**Dopo che funziona** puoi chiudere sul server le porte che non servono più dalla rete locale, togliendo da `ports:` di `docker-compose.yml` quelle di Postgres, pgAdmin e dei servizi (5432–5434, 5050, 8080, 8082) e, se vuoi, anche `3000`.

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
- ✅ App per il telefono provata in Chromium emulando un iPhone: navigazione in basso e menu *Altro*, nessuna pagina che scorre in orizzontale, scatto con due foto (una "di lato" con rotazione EXIF, che arriva dritta all'AI), anteprime e righe a schede, manifest senza errori, service worker attivo e avvio senza rete.
- ✅ Categorie automatiche provate con servizi reali (suggerimento, apprendimento, correzione, storico, isolamento tra utenti) e nel browser (import e pagina Categorie).
- ✅ Doppio conteggio scontrino/banca provato nei due ordini (scontrino poi estratto conto, estratto conto poi scontrino) e nel browser.
- ⚠️ **La lettura vera con Claude non è stata provata:** nei test la risposta dell'AI era simulata (verificati la richiesta inviata e l'elaborazione della risposta, non la qualità della lettura). Va provata con scontrini reali.
- ⚠️ **Tailscale non provato:** nell'ambiente non c'era un account Tailscale né Docker. La configurazione (`tailscale/serve.json`, il servizio nel compose, i comandi) è scritta in base al codice sorgente ufficiale di Tailscale e controllata nella sintassi, ma non è mai stata avviata.
- ⚠️ **Non provata su telefoni veri** (Android, Safari su iPhone): l'emulazione non riproduce fotocamera, installazione e comportamenti di Safari. Anche la configurazione di nginx (limite di upload, timeout, regole per `sw.js`) non è stata avviata perché nginx non c'era nell'ambiente.
- ⚠️ Non provati: `docker compose up`, `deploy.sh`, il manifest Kubernetes e il CronJob di backup (nel mio ambiente non c'erano Docker né Kubernetes).

### Limiti noti e cose aperte

- **Token:** non scade mai ed è salvato in chiaro nel DB; un nuovo login invalida il precedente (un dispositivo alla volta); `user-service` viene interrogato a ogni richiesta (nessuna cache).
- **Ricorrenze solo mensili:** il campo `recurrenceInterval` esiste ma la logica gestisce solo "ogni mese al giorno X".
- **Categorie automatiche:** la parola chiave imparata è la prima parola significativa della descrizione (tolte quelle generiche come pagamento, pos, carta, paypal); se è troppo generica (es. `amazon` per spese diverse) correggi la regola o aggiungine una più specifica nella pagina Categorie. Le regole valgono solo nell'import da CSV, non nell'inserimento manuale né negli scontrini. Se importi una riga con una categoria sbagliata e la casella è attiva, la regola impara lo sbaglio: si corregge o si elimina dalla pagina Categorie.
- **Abbinamento scontrino/banca:** richiede lo stesso importo esatto e una data entro 3 giorni; se la banca registra un importo diverso (mancia, arrotondamento, pagamento diviso) non abbina e va collegato o corretto a mano. Ogni scontrino copre al massimo un movimento.
- **Import:** se due acquisti identici nello stesso giorno sono davvero distinti ma già uno è presente, il secondo viene saltato (cambia la descrizione per importarlo). Il flag "già presente" dell'anteprima non si aggiorna se modifichi una riga; il controllo definitivo avviene al salvataggio.
- **Scontrini:** le foto non vengono conservate (solo i dati estratti); formato HEIC non supportato dal server (il browser di solito lo converte); il costo per scontrino va misurato.
- **Kubernetes:** nessun Ingress/TLS (non serve sulla rete di casa); `ddl-auto=update` al posto di migrazioni (Flyway/Liquibase); stessa password per i due database.
- **Frontend:** bundle > 500 kB (manca il code-splitting).
