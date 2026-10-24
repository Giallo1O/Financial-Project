import React, { useState, useEffect, useCallback } from 'react';
import { Users, Plus, Trash2, Copy, LogIn, LogOut, HandCoins, Share2 } from 'lucide-react';
import {
  fetchGroups, createGroup, joinGroup, createGroupInvite, revokeGroupInvite, leaveGroup,
  fetchGroupSummary, addGroupExpense, deleteGroupExpense, addGroupSettlement, deleteGroupSettlement,
  getCurrentUserId
} from '../api';
import PrivacyMask from '../components/PrivacyMask';
import './SharedExpenses.css';

const eur = (n) => Number(n || 0).toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
const day = (d) => new Date(d).toLocaleDateString('it-IT');
const today = () => new Date().toISOString().slice(0, 10);
const ACTIVE_KEY = 'fm_group';
const blank = () => ({ description: '', amount: '', paidBy: '', date: today(), participants: null }); // null = tutti

const SharedExpenses = () => {
  const me = getCurrentUserId();
  const [groups, setGroups] = useState([]);
  const [activeId, setActiveId] = useState(() => { try { return localStorage.getItem(ACTIVE_KEY) || ''; } catch { return ''; } });
  const [summary, setSummary] = useState(null);
  const [message, setMessage] = useState(null);
  const [form, setForm] = useState(null);
  const [showJoin, setShowJoin] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [copied, setCopied] = useState(false);

  const active = groups.find(g => g.id === activeId) || groups[0];
  const activeKey = active?.id;
  const fail = (err) => setMessage(err.message || 'Operazione non riuscita');

  const loadGroups = useCallback(async () => {
    try { setGroups(await fetchGroups()); } catch (err) { setMessage(err.message || 'Impossibile caricare i gruppi'); }
  }, []);

  const loadSummary = useCallback(async () => {
    if (!activeKey) return;
    try { setSummary(await fetchGroupSummary(activeKey)); } catch (err) { setMessage(err.message); }
  }, [activeKey]);

  // caricamento all'apertura della pagina e al cambio di gruppo
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadGroups(); }, [loadGroups]);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadSummary(); }, [loadSummary]);

  const refresh = () => Promise.all([loadGroups(), loadSummary()]);
  const members = summary?.group.members || [];
  const nameOf = (id) => (id === me ? 'Tu' : members.find(m => m.userId === id)?.username || 'Ex membro');
  const pick = (id) => {
    setActiveId(id); setSummary(null); setForm(null);
    try { localStorage.setItem(ACTIVE_KEY, id); } catch { /* storage non disponibile */ }
  };

  const newGroup = async () => {
    const name = window.prompt('Nome del gruppo', 'Spese in due');
    if (!name) return;
    try { const g = await createGroup(name); await loadGroups(); pick(g.id); } catch (err) { fail(err); }
  };

  const join = async (e) => {
    e.preventDefault();
    try {
      const g = await joinGroup(joinCode);
      setJoinCode(''); setShowJoin(false); setMessage(null);
      await loadGroups(); pick(g.id);
    } catch (err) { fail(err); }
  };

  const share = async () => { try { await createGroupInvite(active.id); await refresh(); setCopied(false); } catch (err) { fail(err); } };
  const unshare = async () => { try { await revokeGroupInvite(active.id); await refresh(); } catch (err) { fail(err); } };
  const copyCode = async () => {
    try { await navigator.clipboard.writeText(summary.group.inviteCode); setCopied(true); } catch { /* il codice resta selezionabile */ }
  };

  const leave = async () => {
    if (!window.confirm(`Uscire dal gruppo «${active.name}»? Non vedrai più le sue spese.`)) return;
    try { await leaveGroup(active.id); setActiveId(''); setSummary(null); await loadGroups(); } catch (err) { fail(err); }
  };

  const saveExpense = async (e) => {
    e.preventDefault();
    setMessage(null);
    try {
      await addGroupExpense(active.id, {
        description: form.description, amount: Number(form.amount), date: form.date,
        paidBy: form.paidBy || null, participants: form.participants,
      });
      setForm(null);
      await refresh();
    } catch (err) { fail(err); }
  };

  const toggleParticipant = (id) => {
    const current = form.participants || members.map(m => m.userId);
    const next = current.includes(id) ? current.filter(x => x !== id) : [...current, id];
    setForm({ ...form, participants: next.length === members.length ? null : next });
  };

  const settle = async (debt) => {
    try { await addGroupSettlement(active.id, { from: debt.from, to: debt.to, amount: debt.amount }); await refresh(); } catch (err) { fail(err); }
  };

  const removeExpense = async (x) => {
    if (!window.confirm(`Eliminare «${x.description}»?`)) return;
    try { await deleteGroupExpense(active.id, x.id); await refresh(); } catch (err) { fail(err); }
  };

  const removeSettlement = async (s) => {
    try { await deleteGroupSettlement(active.id, s.id); await refresh(); } catch (err) { fail(err); }
  };

  const mine = summary ? Number(summary.group.myBalance) : 0;
  const selected = form?.participants || members.map(m => m.userId);

  return (
    <div className="duo-page">
      <header className="dashboard-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div className="duo-logo"><Users size={26} color="white" /></div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Spese in due</h1>
            <p className="text-muted text-sm">Chi ha pagato cosa e chi deve dare quanto.</p>
          </div>
        </div>
      </header>

      {message && <div className="glass-panel duo-message" role="alert">{message}</div>}

      <div className="duo-groups">
        {groups.map(g => (
          <button key={g.id} className={`btn ${active?.id === g.id ? 'btn-primary' : 'btn-glass'}`} onClick={() => pick(g.id)}>{g.name}</button>
        ))}
        <button className="btn btn-glass" onClick={newGroup}><Plus size={16} /> Nuovo</button>
        <button className="btn btn-glass" onClick={() => setShowJoin(s => !s)}><LogIn size={16} /> Unisciti con codice</button>
      </div>

      {showJoin && (
        <form className="glass-panel duo-join" onSubmit={join}>
          <input className="form-input" placeholder="Codice, es. K7M2QX9P" value={joinCode} autoCapitalize="characters"
            onChange={(e) => setJoinCode(e.target.value)} required />
          <button type="submit" className="btn btn-primary">Unisciti</button>
        </form>
      )}

      {groups.length === 0 && (
        <div className="glass-panel duo-empty">
          Crea un gruppo (es. «Casa») e dai il codice all'altra persona, oppure inserisci il codice che ti hanno dato.
        </div>
      )}

      {active && summary && (
        <>
          <div className="glass-panel duo-balance" data-testid="duo-balance">
            {Math.abs(mine) < 0.005 ? <strong>Siete pari</strong> : mine > 0
              ? <><span className="text-muted">Ti devono</span><strong className="pos"><PrivacyMask>{eur(mine)}</PrivacyMask></strong></>
              : <><span className="text-muted">Devi dare</span><strong className="neg"><PrivacyMask>{eur(-mine)}</PrivacyMask></strong></>}
          </div>

          {summary.debts.length > 0 && (
            <div className="glass-panel duo-debts">
              {summary.debts.map(d => (
                <div key={`${d.from}-${d.to}`} className="duo-debt">
                  <span>{nameOf(d.from)} → {nameOf(d.to)}</span>
                  <strong><PrivacyMask>{eur(d.amount)}</PrivacyMask></strong>
                  <button className="btn btn-glass" onClick={() => settle(d)} title="Registra che questi soldi sono stati dati">
                    <HandCoins size={16} /> Pagato
                  </button>
                </div>
              ))}
            </div>
          )}

          {form ? (
            <form className="glass-panel duo-form" onSubmit={saveExpense}>
              <h3>Nuova spesa</h3>
              <label>Cosa
                <input className="form-input" value={form.description} maxLength={100} autoFocus
                  onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Cena, spesa, bolletta…" />
              </label>
              <label>Importo (€)
                <input type="number" step="0.01" min="0.01" className="form-input" value={form.amount} required
                  onChange={(e) => setForm({ ...form, amount: e.target.value })} />
              </label>
              <label>Pagato da
                <select className="form-select" value={form.paidBy || me} onChange={(e) => setForm({ ...form, paidBy: e.target.value })}>
                  {members.map(m => <option key={m.userId} value={m.userId}>{m.userId === me ? 'Me' : m.username}</option>)}
                </select>
              </label>
              <label>Data
                <input type="date" className="form-input" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
              </label>
              <fieldset className="duo-who">
                <legend>Divisa tra</legend>
                {members.map(m => (
                  <label key={m.userId} className="duo-check">
                    <input type="checkbox" checked={selected.includes(m.userId)} onChange={() => toggleParticipant(m.userId)} />
                    {nameOf(m.userId)}
                  </label>
                ))}
              </fieldset>
              <div className="duo-row">
                <button type="submit" className="btn btn-primary" disabled={selected.length === 0}>Salva</button>
                <button type="button" className="btn btn-glass" onClick={() => setForm(null)}>Annulla</button>
              </div>
            </form>
          ) : (
            <button className="btn btn-primary duo-add" onClick={() => setForm({ ...blank(), paidBy: me })}>
              <Plus size={18} /> Nuova spesa
            </button>
          )}

          <div className="glass-panel duo-list">
            {summary.expenses.length === 0 && <p className="text-muted" style={{ padding: '0.5rem 0' }}>Ancora nessuna spesa.</p>}
            {summary.expenses.map(x => {
              const myShare = x.shares.find(s => s.userId === me);
              return (
                <div key={x.id} className="duo-item">
                  <div>
                    <div className="duo-title">{x.description}</div>
                    <div className="text-muted text-sm">
                      {day(x.date)} · pagato da {nameOf(x.paidBy)}
                      {x.shares.length < members.length && ` · tra ${x.shares.map(s => nameOf(s.userId)).join(', ')}`}
                    </div>
                  </div>
                  <div className="duo-amount">
                    <strong><PrivacyMask>{eur(x.amount)}</PrivacyMask></strong>
                    {myShare && <div className="text-muted text-sm">tua parte <PrivacyMask>{eur(myShare.amount)}</PrivacyMask></div>}
                  </div>
                  <button className="icon-btn" aria-label={`Elimina ${x.description}`} onClick={() => removeExpense(x)}><Trash2 size={16} /></button>
                </div>
              );
            })}
            {summary.settlements.map(s => (
              <div key={s.id} className="duo-item settled">
                <div>
                  <div className="duo-title">{nameOf(s.from)} ha dato a {nameOf(s.to)}</div>
                  <div className="text-muted text-sm">{day(s.date)} · pareggio</div>
                </div>
                <div className="duo-amount"><strong><PrivacyMask>{eur(s.amount)}</PrivacyMask></strong></div>
                <button className="icon-btn" aria-label="Elimina pareggio" onClick={() => removeSettlement(s)}><Trash2 size={16} /></button>
              </div>
            ))}
          </div>

          <div className="glass-panel duo-share">
            <div className="text-muted text-sm">Nel gruppo: {members.map(m => m.username).join(', ')}</div>
            {summary.group.inviteCode ? (
              <div className="duo-row">
                <code className="duo-code" data-testid="duo-code">{summary.group.inviteCode}</code>
                <button className="btn btn-glass" onClick={copyCode}><Copy size={16} /> {copied ? 'Copiato' : 'Copia'}</button>
                <button className="btn btn-glass" onClick={unshare}>Disattiva</button>
              </div>
            ) : (
              <button className="btn btn-glass" onClick={share}><Share2 size={16} /> Crea un codice di invito</button>
            )}
            <button className="btn btn-glass" onClick={leave}><LogOut size={16} /> Esci dal gruppo</button>
          </div>
        </>
      )}
    </div>
  );
};

export default SharedExpenses;
