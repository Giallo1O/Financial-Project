import React, { useCallback, useEffect, useRef, useState } from 'react';
import { User, Home, Link2, Unlink, AlertTriangle, CheckCircle, ExternalLink } from 'lucide-react';
import {
  fetchHomeAssistantStatus, startHomeAssistantLink, unlinkHomeAssistant, fetchHomeAssistantLists,
  chooseHomeAssistantList,
} from '../api';
import { getUserInfo } from '../auth';
import './Profile.css';

const LAST_URL_KEY = 'fm_ha_url'; // comodita': ricorda l'ultimo indirizzo scritto

const Profile = () => {
  const user = getUserInfo();
  const [status, setStatus] = useState(null);
  const [url, setUrl] = useState(() => { try { return localStorage.getItem(LAST_URL_KEY) || ''; } catch { return ''; } });
  const [lists, setLists] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const watch = useRef(null);

  const load = useCallback(async () => {
    try {
      const s = await fetchHomeAssistantStatus();
      setStatus(s);
      if (s.url) setUrl(s.url);
      if (s.linked && s.valid) {
        setLists(await fetchHomeAssistantLists());
      }
    } catch (err) {
      setError(err.message);
    }
  }, []);

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load(); }, [load]);

  // La finestra del login avvisa quando ha finito; se si chiude da sola (o la chiudi tu) ricarico lo stato
  useEffect(() => {
    const onMessage = (e) => {
      if (e.origin === window.location.origin && e.data?.type === 'ha-linked') {
        setNotice('Account Home Assistant collegato.');
        load();
      }
    };
    window.addEventListener('message', onMessage);
    return () => { window.removeEventListener('message', onMessage); clearInterval(watch.current); };
  }, [load]);

  const connect = async () => {
    setError(''); setNotice('');
    // App installata sul telefono: niente popup, il login si apre nella stessa finestra e poi torna qui
    const standalone = window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone;
    // Il popup va aperto subito (nel clic), altrimenti il browser lo blocca; l'indirizzo arriva dopo
    const popup = standalone ? null : window.open('', 'ha-login', 'width=520,height=760');
    setBusy(true);
    try {
      const authorizeUrl = await startHomeAssistantLink(url);
      try { localStorage.setItem(LAST_URL_KEY, url); } catch { /* ignora */ }
      if (!popup || popup.closed) {
        window.location.href = authorizeUrl;
        return;
      }
      popup.location.href = authorizeUrl;
      clearInterval(watch.current);
      watch.current = setInterval(() => {
        if (popup.closed) { clearInterval(watch.current); setBusy(false); load(); }
      }, 700);
    } catch (err) {
      popup?.close();
      setError(err.message);
      setBusy(false);
    }
  };

  const disconnect = async () => {
    if (!window.confirm('Scollegare l\'account Home Assistant? La lista di casa sparirà da FinManager (in Home Assistant non cambia nulla).')) return;
    setBusy(true); setError(''); setNotice('');
    try { await unlinkHomeAssistant(); setLists([]); await load(); setNotice('Account scollegato.'); } catch (err) { setError(err.message); }
    setBusy(false);
  };

  const choose = async (entityId) => {
    setError('');
    try { setStatus(await chooseHomeAssistantList(entityId)); setNotice('Lista della spesa aggiornata.'); } catch (err) { setError(err.message); }
  };

  const linkedOk = status?.linked && status?.valid;

  return (
    <div className="profile-page">
      <header className="profile-header">
        <div className="profile-avatar"><User size={26} color="white" /></div>
        <div>
          <h1>Profilo</h1>
          <p className="text-muted text-sm">Connesso come <strong>{user?.username}</strong></p>
        </div>
      </header>

      <section className="glass-panel profile-card">
        <h3 className="profile-card-title"><Home size={20} /> Home Assistant</h3>
        <p className="text-muted text-sm">
          Collega il tuo account di Home Assistant per vedere e modificare da FinManager la lista della spesa di casa
          (la stessa dell'app di Home Assistant e dell'assistente vocale). Accedi con le tue credenziali di Home Assistant:
          FinManager non le vede, riceve solo un permesso che puoi revocare quando vuoi.
        </p>

        {error && <div className="profile-alert error" role="alert"><AlertTriangle size={16} /> {error}</div>}
        {notice && <div className="profile-alert ok"><CheckCircle size={16} /> {notice}</div>}

        {!status ? (
          <p className="text-muted">Caricamento…</p>
        ) : !status.available ? (
          <div className="profile-alert error"><AlertTriangle size={16} /> Il collegamento non è attivo su questo server (manca la chiave APP_SECRET_KEY).</div>
        ) : linkedOk ? (
          <div className="profile-linked">
            <p>
              <CheckCircle size={16} className="text-success" /> Collegato a <a href={status.url} target="_blank" rel="noreferrer">{status.url} <ExternalLink size={12} /></a>
              {status.linkedAt && <span className="text-muted text-sm"> · dal {new Date(status.linkedAt).toLocaleDateString('it-IT')}</span>}
            </p>
            <label className="profile-label" htmlFor="ha-list">Lista della spesa</label>
            <div className="input-wrapper">
              <select id="ha-list" value={status.entityId || ''} onChange={(e) => choose(e.target.value)}>
                <option value="" disabled>Scegli una lista…</option>
                {lists.map(l => <option key={l.entityId} value={l.entityId}>{l.name} ({l.entityId})</option>)}
              </select>
            </div>
            {!status.entityId && <p className="text-sm text-muted">Scegli la lista da mostrare in «Lista spesa».</p>}
            <button className="btn btn-glass profile-danger" onClick={disconnect} disabled={busy}><Unlink size={16} /> Scollega</button>
          </div>
        ) : (
          <form className="profile-connect" onSubmit={(e) => { e.preventDefault(); connect(); }}>
            {status.linked && !status.valid && (
              <div className="profile-alert error"><AlertTriangle size={16} /> Home Assistant ha revocato il collegamento: accedi di nuovo per ripristinarlo.</div>
            )}
            <label className="profile-label" htmlFor="ha-url">Indirizzo di Home Assistant</label>
            <div className="input-wrapper">
              <input id="ha-url" type="url" required placeholder="https://homeassistant.casa.it" value={url}
                onChange={(e) => setUrl(e.target.value)} autoComplete="url" />
            </div>
            <p className="text-sm text-muted">Lo stesso indirizzo che apri nel browser per usare Home Assistant.</p>
            <button type="submit" className="btn btn-primary" disabled={busy || !url}>
              <Link2 size={16} /> {busy ? 'In attesa del login…' : status.linked ? 'Ricollega' : 'Collega account Home Assistant'}
            </button>
          </form>
        )}
      </section>
    </div>
  );
};

export default Profile;
