import React, { useState, useEffect, useCallback } from 'react';
import { Landmark, Plus, Trash2, Pencil, ArrowRightLeft, Archive, ArchiveRestore } from 'lucide-react';
import {
  fetchAccounts, createAccount, updateAccount, deleteAccount, assignUnassignedToAccount,
  fetchTransfers, createTransfer, deleteTransfer
} from '../api';
import PrivacyMask from '../components/PrivacyMask';
import './Accounts.css';

const TYPES = [
  ['CORRENTE', 'Conto corrente'], ['CARTA', 'Carta'], ['CONTANTI', 'Contanti'],
  ['RISPARMIO', 'Risparmio'], ['INVESTIMENTI', 'Investimenti'], ['ALTRO', 'Altro'],
];
const typeName = (t) => (TYPES.find(([k]) => k === t) || [0, 'Altro'])[1];
const eur = (n) => Number(n || 0).toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
const today = () => new Date().toISOString().slice(0, 10);

const Accounts = () => {
  const [data, setData] = useState({ accounts: [], unassigned: 0, hasUnassigned: false, total: 0 });
  const [transfers, setTransfers] = useState([]);
  const [message, setMessage] = useState(null);
  const [form, setForm] = useState(null); // { id?, name, type, initialBalance }
  const [move, setMove] = useState({ fromAccountId: '', toAccountId: '', amount: '', date: today(), description: '' });
  const [target, setTarget] = useState('');

  const fail = (err) => setMessage(err.message || 'Operazione non riuscita');

  const load = useCallback(async () => {
    try {
      const [overview, list] = await Promise.all([fetchAccounts(), fetchTransfers()]);
      setData(overview);
      setTransfers(list);
    } catch (err) { fail(err); }
  }, []);

  // caricamento all'apertura della pagina
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load(); }, [load]);

  const byId = Object.fromEntries(data.accounts.map(a => [a.id, a]));
  const active = data.accounts.filter(a => !a.archived);

  const save = async (e) => {
    e.preventDefault();
    setMessage(null);
    const body = { name: form.name, type: form.type, initialBalance: Number(form.initialBalance) || 0 };
    try {
      await (form.id ? updateAccount(form.id, body) : createAccount(body));
      setForm(null);
      load();
    } catch (err) { fail(err); }
  };

  const remove = async (a) => {
    if (!window.confirm(`Eliminare il conto «${a.name}»? I suoi movimenti restano, ma senza conto.`)) return;
    try { await deleteAccount(a.id); load(); } catch (err) { fail(err); }
  };

  const toggleArchive = async (a) => {
    try {
      await updateAccount(a.id, { name: a.name, type: a.type, initialBalance: a.initialBalance, archived: !a.archived });
      load();
    } catch (err) { fail(err); }
  };

  const assign = async () => {
    try {
      const { assigned } = await assignUnassignedToAccount(target);
      setMessage(`${assigned} movimenti spostati su «${byId[target].name}».`);
      setTarget('');
      load();
    } catch (err) { fail(err); }
  };

  const submitMove = async (e) => {
    e.preventDefault();
    setMessage(null);
    try {
      await createTransfer({ ...move, amount: Number(move.amount) });
      setMove({ ...move, amount: '', description: '' });
      load();
    } catch (err) { fail(err); }
  };

  const removeMove = async (t) => {
    try { await deleteTransfer(t.id); load(); } catch (err) { fail(err); }
  };

  return (
    <div className="acc-page">
      <header className="dashboard-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div className="acc-logo"><Landmark size={26} color="white" /></div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Conti</h1>
            <p className="text-muted text-sm">Quanto hai e dove. I giroconti non contano come spese.</p>
          </div>
        </div>
      </header>

      {message && <div className="glass-panel acc-message" role="status">{message}</div>}

      <div className="glass-panel acc-total">
        <span className="text-muted">Patrimonio totale</span>
        <strong data-testid="acc-total"><PrivacyMask>{eur(data.total)}</PrivacyMask></strong>
      </div>

      {data.hasUnassigned && active.length > 0 && (
        <div className="glass-panel acc-unassigned">
          <p>
            Ci sono movimenti senza conto (saldo {eur(data.unassigned)}). Scegli il conto su cui metterli tutti:
          </p>
          <div className="acc-row">
            <select className="form-select" value={target} onChange={(e) => setTarget(e.target.value)} aria-label="Conto di destinazione">
              <option value="">Scegli il conto…</option>
              {active.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
            <button className="btn btn-primary" disabled={!target} onClick={assign}>Sposta</button>
          </div>
        </div>
      )}

      <div className="acc-list">
        {data.accounts.map(a => (
          <div key={a.id} className={`glass-panel acc-card ${a.archived ? 'archived' : ''}`}>
            <div>
              <div className="acc-name">{a.name}{a.archived && <span className="text-muted text-sm"> · archiviato</span>}</div>
              <div className="text-muted text-sm">{typeName(a.type)}</div>
            </div>
            <div className="acc-balance"><PrivacyMask>{eur(a.balance)}</PrivacyMask></div>
            <div className="acc-actions">
              <button className="icon-btn" title="Modifica" aria-label={`Modifica ${a.name}`}
                onClick={() => setForm({ id: a.id, name: a.name, type: a.type, initialBalance: a.initialBalance })}><Pencil size={16} /></button>
              <button className="icon-btn" title={a.archived ? 'Riattiva' : 'Archivia'} aria-label={`Archivia ${a.name}`}
                onClick={() => toggleArchive(a)}>{a.archived ? <ArchiveRestore size={16} /> : <Archive size={16} />}</button>
              <button className="icon-btn" title="Elimina" aria-label={`Elimina ${a.name}`} onClick={() => remove(a)}><Trash2 size={16} /></button>
            </div>
          </div>
        ))}
        {data.accounts.length === 0 && (
          <div className="glass-panel acc-empty">Nessun conto: aggiungi la banca, le contanti, la carta…</div>
        )}
      </div>

      {form ? (
        <form className="glass-panel acc-form" onSubmit={save}>
          <h3>{form.id ? 'Modifica conto' : 'Nuovo conto'}</h3>
          <label>Nome
            <input className="form-input" value={form.name} maxLength={40} required autoFocus
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          <label>Tipo
            <select className="form-select" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              {TYPES.map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label>Saldo iniziale (€)
            <input type="number" step="0.01" className="form-input" value={form.initialBalance}
              onChange={(e) => setForm({ ...form, initialBalance: e.target.value })} />
          </label>
          <div className="acc-row">
            <button type="submit" className="btn btn-primary">Salva</button>
            <button type="button" className="btn btn-glass" onClick={() => setForm(null)}>Annulla</button>
          </div>
        </form>
      ) : (
        <button className="btn btn-glass acc-add" onClick={() => setForm({ name: '', type: 'CORRENTE', initialBalance: '' })}>
          <Plus size={16} /> Nuovo conto
        </button>
      )}

      {active.length >= 2 && (
        <>
          <h2 className="acc-h2"><ArrowRightLeft size={18} /> Giroconto</h2>
          <form className="glass-panel acc-form" onSubmit={submitMove}>
            <label>Da
              <select className="form-select" value={move.fromAccountId} required
                onChange={(e) => setMove({ ...move, fromAccountId: e.target.value })}>
                <option value="">Scegli…</option>
                {active.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </label>
            <label>A
              <select className="form-select" value={move.toAccountId} required
                onChange={(e) => setMove({ ...move, toAccountId: e.target.value })}>
                <option value="">Scegli…</option>
                {active.filter(a => a.id !== move.fromAccountId).map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </label>
            <label>Importo (€)
              <input type="number" step="0.01" min="0.01" className="form-input" value={move.amount} required
                onChange={(e) => setMove({ ...move, amount: e.target.value })} />
            </label>
            <label>Data
              <input type="date" className="form-input" value={move.date}
                onChange={(e) => setMove({ ...move, date: e.target.value })} />
            </label>
            <label>Nota (facoltativa)
              <input className="form-input" value={move.description} maxLength={100}
                onChange={(e) => setMove({ ...move, description: e.target.value })} />
            </label>
            <button type="submit" className="btn btn-primary">Sposta i soldi</button>
          </form>
        </>
      )}

      {transfers.length > 0 && (
        <div className="glass-panel acc-transfers">
          {transfers.map(t => (
            <div key={t.id} className="acc-transfer">
              <div>
                <div>{byId[t.fromAccountId]?.name || '?'} → {byId[t.toAccountId]?.name || '?'}</div>
                <div className="text-muted text-sm">{new Date(t.transferDate).toLocaleDateString('it-IT')}{t.description ? ` · ${t.description}` : ''}</div>
              </div>
              <strong><PrivacyMask>{eur(t.amount)}</PrivacyMask></strong>
              <button className="icon-btn" aria-label="Elimina giroconto" onClick={() => removeMove(t)}><Trash2 size={16} /></button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Accounts;
