import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  ListChecks, Plus, Minus, Trash2, Check, Users, Copy, X, RefreshCw, LogIn, Share2
} from 'lucide-react';
import {
  fetchShoppingLists, createShoppingList, deleteOrLeaveShoppingList, createShoppingInvite, revokeShoppingInvite,
  joinShoppingList, fetchShoppingItems, addShoppingItem, updateShoppingItem, deleteShoppingItem,
  clearCheckedShoppingItems, fetchShoppingSuggestions
} from '../api';
import './Shopping.css';
import { eur } from '../utils/format';

const qtyText = (q) => Number(q).toLocaleString('it-IT', { maximumFractionDigits: 3 });
const norm = (s) => (s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
const step = (unit) => (unit === 'kg' || unit === 'l' ? 0.5 : 1);
const ACTIVE_KEY = 'fm_shop_list';

const Shopping = () => {
  const [lists, setLists] = useState([]);
  const [activeId, setActiveId] = useState(() => { try { return localStorage.getItem(ACTIVE_KEY) || ''; } catch { return ''; } });
  const [items, setItems] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [newName, setNewName] = useState('');
  const [message, setMessage] = useState(null);
  const [showShare, setShowShare] = useState(false);
  const [showJoin, setShowJoin] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [copied, setCopied] = useState(false);
  const creating = useRef(false);

  const active = lists.find(l => l.id === activeId) || lists[0];
  const activeKey = active?.id;
  const fail = (err) => setMessage(err.message || 'Operazione non riuscita');

  const loadLists = useCallback(async () => {
    try {
      let data = await fetchShoppingLists();
      if (data.length === 0 && !creating.current) { // prima visita: pronta una lista "Spesa"
        creating.current = true;
        data = [await createShoppingList('Spesa')];
      }
      setLists(data);
    } catch (err) {
      setMessage(err.message || 'Impossibile caricare le liste');
    }
  }, []);

  const reloadItems = useCallback(async () => {
    if (!activeKey) return;
    try { setItems(await fetchShoppingItems(activeKey)); } catch { /* si riprova al prossimo giro */ }
  }, [activeKey]);

  // caricamento all'apertura della pagina
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadLists(); fetchShoppingSuggestions().then(setSuggestions).catch(() => { }); }, [loadLists]);

  // La lista si aggiorna da sola: chi e' nel negozio vede cosa ha aggiunto o preso l'altra persona
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    reloadItems();
    const refresh = () => { if (!document.hidden) { reloadItems(); loadLists(); } };
    const timer = setInterval(refresh, 8000);
    document.addEventListener('visibilitychange', refresh);
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', refresh); };
  }, [reloadItems, loadLists]);

  const pick = (id) => {
    setActiveId(id);
    try { localStorage.setItem(ACTIVE_KEY, id); } catch { /* ignora */ }
    setShowShare(false);
    setItems([]);
  };

  const add = async (name, unit = 'pz') => {
    if (!active || !name.trim()) return;
    try {
      await addShoppingItem(active.id, { name: name.trim(), quantity: 1, unit });
      setNewName('');
      setMessage(null);
      await Promise.all([reloadItems(), loadLists()]);
    } catch (err) { fail(err); }
  };

  const submit = (e) => {
    e.preventDefault();
    const known = suggestions.find(s => norm(s.name) === norm(newName)); // se lo conosci, l'unita' (kg, pz...) viene da li'
    add(newName, known?.unit || 'pz');
  };

  const toggle = async (item) => {
    setItems(prev => prev.map(i => (i.id === item.id ? { ...i, checked: !i.checked } : i))); // subito, senza aspettare la rete
    try { await updateShoppingItem(active.id, item.id, { checked: !item.checked }); await loadLists(); } catch (err) { fail(err); reloadItems(); }
  };

  const changeQty = async (item, delta) => {
    const next = Number(item.quantity) + delta * step(item.unit);
    if (next <= 0) return;
    setItems(prev => prev.map(i => (i.id === item.id ? { ...i, quantity: next } : i)));
    try { await updateShoppingItem(active.id, item.id, { quantity: next }); } catch (err) { fail(err); reloadItems(); }
  };

  const remove = async (item) => {
    try { await deleteShoppingItem(active.id, item.id); await Promise.all([reloadItems(), loadLists()]); } catch (err) { fail(err); }
  };

  const clearTaken = async () => {
    try { await clearCheckedShoppingItems(active.id); await Promise.all([reloadItems(), loadLists()]); } catch (err) { fail(err); }
  };

  const newList = async () => {
    const name = window.prompt('Nome della nuova lista', 'Spesa');
    if (!name) return;
    try { const l = await createShoppingList(name); await loadLists(); pick(l.id); } catch (err) { fail(err); }
  };

  const join = async (e) => {
    e.preventDefault();
    try {
      const l = await joinShoppingList(joinCode);
      setJoinCode(''); setShowJoin(false); setMessage(null);
      await loadLists(); pick(l.id);
    } catch (err) { fail(err); }
  };

  const share = async () => {
    try { await createShoppingInvite(active.id); await loadLists(); setCopied(false); } catch (err) { fail(err); }
  };

  const unshare = async () => {
    try { await revokeShoppingInvite(active.id); await loadLists(); } catch (err) { fail(err); }
  };

  const copyCode = async () => {
    try { await navigator.clipboard.writeText(active.inviteCode); setCopied(true); } catch { /* il codice resta selezionabile a mano */ }
  };

  const leaveOrDelete = async () => {
    const text = active.mine
      ? `Eliminare la lista «${active.name}» per tutti?`
      : `Uscire dalla lista «${active.name}»? Resta all'altra persona.`;
    if (!window.confirm(text)) return;
    try { await deleteOrLeaveShoppingList(active.id); setActiveId(''); setItems([]); await loadLists(); } catch (err) { fail(err); }
  };

  const shared = (active?.members.length || 0) > 1; // chi ha aggiunto una voce si mostra solo se la lista e' condivisa
  const open = items.filter(i => !i.checked);
  const taken = items.filter(i => i.checked);
  const onList = new Set(open.map(i => norm(i.name)));
  const due = suggestions.filter(s => s.due && !onList.has(norm(s.name))).slice(0, 8);
  const usual = suggestions.filter(s => !s.due && !onList.has(norm(s.name))).slice(0, 8);

  let estimate = 0; let cheapest = 0; let priced = 0;
  open.forEach(i => {
    if (i.price) {
      priced += 1;
      estimate += Number(i.quantity) * Number(i.price.lastUnitPrice);
      cheapest += Number(i.quantity) * Number(i.price.minUnitPrice);
    }
  });

  return (
    <div className="shop-page">
      <header className="dashboard-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div className="shop-logo"><ListChecks size={26} color="white" /></div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Lista della spesa</h1>
            <p className="text-muted text-sm">Cosa comprare, da spuntare mentre sei in negozio.</p>
          </div>
        </div>
      </header>

      {message && <div className="glass-panel shop-message" role="alert">{message}</div>}

      <div className="shop-lists">
        {lists.map(l => (
          <button key={l.id} className={`btn ${active?.id === l.id ? 'btn-primary' : 'btn-glass'}`} onClick={() => pick(l.id)}>
            {l.name}{!l.mine && <span className="text-sm"> · di {l.ownerName}</span>} {l.openItems > 0 && <span className="shop-badge">{l.openItems}</span>}
            {l.members.length > 1 && <Users size={14} />}
          </button>
        ))}
        <button className="btn btn-glass" onClick={newList}><Plus size={16} /> Nuova</button>
        <button className="btn btn-glass" onClick={() => setShowJoin(s => !s)}><LogIn size={16} /> Unisciti con codice</button>
      </div>

      {showJoin && (
        <form className="glass-panel shop-join" onSubmit={join}>
          <input className="form-input" placeholder="Codice, es. K7M2QX9P" value={joinCode} autoCapitalize="characters"
            onChange={(e) => setJoinCode(e.target.value)} required />
          <button type="submit" className="btn btn-primary">Unisciti</button>
        </form>
      )}

      {active && (
        <>
          <form className="shop-add" onSubmit={submit}>
            <input className="form-input" list="shop-names" placeholder="Aggiungi: latte, pane, uova…" value={newName}
              onChange={(e) => setNewName(e.target.value)} aria-label="Cosa aggiungere" />
            <datalist id="shop-names">
              {suggestions.map(s => <option key={`${s.name}|${s.unit}`} value={s.name} />)}
            </datalist>
            <button type="submit" className="btn btn-primary" disabled={!newName.trim()}><Plus size={20} /> Aggiungi</button>
          </form>

          {due.length > 0 && (
            <section className="shop-suggest">
              <h4><RefreshCw size={15} /> Da ricomprare</h4>
              <div className="shop-chips">
                {due.map(s => (
                  <button key={`${s.name}|${s.unit}`} className="shop-chip due" onClick={() => add(s.name, s.unit)}
                    title={`Lo compri ogni ${s.avgIntervalDays} giorni circa; l'ultima volta ${s.daysSinceLast} giorni fa`}>
                    <Plus size={14} /> {s.name}
                  </button>
                ))}
              </div>
            </section>
          )}

          {usual.length > 0 && (
            <section className="shop-suggest">
              <h4>Di solito compri</h4>
              <div className="shop-chips">
                {usual.map(s => (
                  <button key={`${s.name}|${s.unit}`} className="shop-chip" onClick={() => add(s.name, s.unit)}>
                    <Plus size={14} /> {s.name}
                  </button>
                ))}
              </div>
            </section>
          )}

          <section className="glass-panel shop-items">
            {open.length === 0 && taken.length === 0 && (
              <p className="text-muted" style={{ padding: '0.5rem 0' }}>La lista è vuota. Aggiungi qualcosa qui sopra.</p>
            )}
            {open.map(i => (
              <ItemRow key={i.id} item={i} showAdder={shared} onToggle={toggle} onQty={changeQty} onDelete={remove} />
            ))}
            {open.length === 0 && taken.length > 0 && (
              <p className="shop-done"><Check size={18} /> Hai preso tutto!</p>
            )}

            {taken.length > 0 && (
              <>
                <div className="shop-taken-head">
                  <span>Presi ({taken.length})</span>
                  <button className="btn btn-glass" onClick={clearTaken}><Trash2 size={16} /> Svuota presi</button>
                </div>
                {taken.map(i => (
                  <ItemRow key={i.id} item={i} showAdder={shared} onToggle={toggle} onQty={changeQty} onDelete={remove} />
                ))}
              </>
            )}
          </section>

          {priced > 0 && (
            <section className="glass-panel shop-estimate">
              <div>Stima della spesa: <strong>{eur(estimate)}</strong>
                <span className="text-muted text-sm"> ({priced} di {open.length} voci con un prezzo noto)</span></div>
              {estimate - cheapest > 0.01 && (
                <div>Comprando ogni cosa dove costa meno: <strong>{eur(cheapest)}</strong>
                  <span className="shop-save"> — risparmi {eur(estimate - cheapest)}</span></div>
              )}
            </section>
          )}

          <section className="glass-panel shop-share">
            <div className="shop-share-head">
              <span><Users size={16} /> {active.members.join(', ')}</span>
              <button className="btn btn-glass" onClick={() => setShowShare(s => !s)}><Share2 size={16} /> Condivisione</button>
            </div>
            {showShare && (
              <div className="shop-share-body">
                {active.mine ? (
                  active.inviteCode ? (
                    <>
                      <p className="text-muted text-sm">Dai questo codice all'altra persona: lo inserisce in «Unisciti con codice» (serve un account FinManager).</p>
                      <div className="shop-code">
                        <code>{active.inviteCode}</code>
                        <button className="btn btn-glass" onClick={copyCode}>{copied ? <Check size={16} /> : <Copy size={16} />} {copied ? 'Copiato' : 'Copia'}</button>
                      </div>
                      <button className="btn btn-glass" onClick={unshare}><X size={16} /> Disattiva il codice</button>
                    </>
                  ) : (
                    <button className="btn btn-primary" onClick={share}><Share2 size={16} /> Crea un codice di invito</button>
                  )
                ) : (
                  <p className="text-muted text-sm">Lista di {active.ownerName}. Solo lui o lei può creare il codice di invito.</p>
                )}
                <button className="btn btn-glass shop-danger" onClick={leaveOrDelete}>
                  <Trash2 size={16} /> {active.mine ? 'Elimina la lista' : 'Esci dalla lista'}
                </button>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
};

const ItemRow = ({ item, showAdder, onToggle, onQty, onDelete }) => (
  <div className={`shop-item ${item.checked ? 'taken' : ''}`}>
    <button className="shop-check" aria-label={item.checked ? `Rimetti ${item.name} in lista` : `Segna ${item.name} come preso`}
      aria-pressed={item.checked} onClick={() => onToggle(item)}>
      {item.checked && <Check size={20} />}
    </button>
    <div className="shop-item-main">
      <div className="shop-item-name">{item.name}</div>
      <div className="shop-item-sub text-muted text-sm">
        {showAdder && item.addedBy && <span>{item.addedBy}</span>}
        {item.price && (
          <span>
            {eur(item.price.lastUnitPrice)}/{item.unit}
            {Number(item.price.minUnitPrice) < Number(item.price.lastUnitPrice) - 0.005 &&
              <> · meglio da <strong>{item.price.cheapestStore}</strong> {eur(item.price.minUnitPrice)}</>}
          </span>
        )}
      </div>
    </div>
    {!item.checked && (
      <div className="shop-qty">
        <button aria-label="Meno" onClick={() => onQty(item, -1)} disabled={Number(item.quantity) - step(item.unit) <= 0}><Minus size={16} /></button>
        <span>{qtyText(item.quantity)} {item.unit}</span>
        <button aria-label="Più" onClick={() => onQty(item, 1)}><Plus size={16} /></button>
      </div>
    )}
    <button className="shop-del" aria-label={`Elimina ${item.name}`} onClick={() => onDelete(item)}><Trash2 size={18} /></button>
  </div>
);

export default Shopping;
