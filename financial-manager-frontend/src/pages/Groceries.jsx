import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import {
  ShoppingCart, ScanLine, Receipt, Tags, PieChart, Trash2, Plus, AlertTriangle, ChevronDown, ChevronUp, Camera, Image as ImageIcon,
  FileText, X
} from 'lucide-react';
import './Groceries.css';
import PrivacyMask from '../components/PrivacyMask';
import { prepareReceiptFile } from '../utils/imageUtils';
import {
  fetchCategories, scanReceipt, saveReceipt, fetchReceipts, fetchReceipt, deleteReceipt,
  fetchProducts, fetchProductHistory, fetchFoodSpending, fetchReceiptMatches
} from '../api';
import { eur } from '../utils/format';

const FOOD_CATEGORIES = [
  'Frutta e verdura', 'Carne e pesce', 'Latticini e uova', 'Pane e cereali', 'Dispensa',
  'Bevande', 'Dolci e snack', 'Surgelati', 'Casa e igiene', 'Altro'
];

const day = (iso) => new Date(iso).toLocaleDateString('it-IT');
const num = (v) => (v === '' || v == null ? 0 : Number(String(v).replace(',', '.')) || 0);

const MAX_FILES = 4;

const TABS = [
  { id: 'scan', label: 'Nuovo scontrino', icon: ScanLine },
  { id: 'receipts', label: 'Scontrini', icon: Receipt },
  { id: 'products', label: 'Prodotti e prezzi', icon: Tags },
  { id: 'spending', label: 'Per tipo', icon: PieChart },
];

const Groceries = () => {
  const [tab, setTab] = useState('scan');
  const [message, setMessage] = useState(null); // { text, type: 'success' | 'error' }

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', animation: 'fadeIn 0.5s ease-out' }}>
      <header className="dashboard-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{
            width: '50px', height: '50px', background: 'var(--accent-gradient)',
            borderRadius: 'var(--radius-md)', display: 'flex', alignItems: 'center',
            justifyContent: 'center', boxShadow: 'var(--shadow-glow)'
          }}>
            <ShoppingCart size={26} color="white" />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Spesa</h1>
            <p className="text-muted text-sm">Carica gli scontrini e tieni traccia di cosa compri e a che prezzo.</p>
          </div>
        </div>
      </header>

      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        {TABS.map((t) => {
          const Icon = t.icon;
          return (
            <button key={t.id} className={`btn ${tab === t.id ? 'btn-primary' : 'btn-glass'}`}
              onClick={() => { setTab(t.id); setMessage(null); }}>
              <Icon size={18} /> {t.label}
            </button>
          );
        })}
      </div>

      {message && (
        <div className="glass-panel" style={{
          padding: '1rem 1.25rem', marginBottom: '1.5rem',
          borderLeft: `4px solid ${message.type === 'success' ? 'var(--success)' : 'var(--danger)'}`
        }}>
          {message.text}
        </div>
      )}

      {tab === 'scan' && (
        <ScanTab setMessage={setMessage} onSaved={() => setTab('receipts')} />
      )}
      {tab === 'receipts' && <ReceiptsTab setMessage={setMessage} />}
      {tab === 'products' && <ProductsTab />}
      {tab === 'spending' && <SpendingTab />}
    </div>
  );
};

// ─── Nuovo scontrino: carica → controlla → salva ───────────────────────────

const ScanTab = ({ setMessage, onSaved }) => {
  const [files, setFiles] = useState([]); // { id, file, url }: url e' l'anteprima (solo immagini)
  const cameraRef = useRef(null);
  const galleryRef = useRef(null);
  const [scanning, setScanning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [draft, setDraft] = useState(null);
  const [categories, setCategories] = useState([]);
  const [categoryId, setCategoryId] = useState('');
  const [matches, setMatches] = useState([]); // movimenti già presenti che potrebbero essere questo scontrino
  const [linkId, setLinkId] = useState('');   // '' = crea una nuova spesa

  const draftDate = draft?.purchaseDate;
  const draftTotal = draft?.total;
  useEffect(() => {
    if (!draftDate || num(draftTotal) <= 0) {
      setMatches([]);
      setLinkId('');
      return undefined;
    }
    let cancelled = false;
    fetchReceiptMatches(draftDate, num(draftTotal))
      .then((found) => { if (!cancelled) { setMatches(found); setLinkId(found[0]?.id || ''); } })
      .catch(() => { if (!cancelled) { setMatches([]); setLinkId(''); } });
    return () => { cancelled = true; };
  }, [draftDate, draftTotal]);

  useEffect(() => {
    fetchCategories().then((cats) => {
      const expenses = cats.filter(c => c.expense);
      setCategories(expenses);
      const food = expenses.find(c => /spesa|aliment|supermerc/i.test(c.name));
      setCategoryId((food || expenses[0])?.id || '');
    }).catch(() => { });
  }, []);

  const addFiles = (list) => {
    const added = Array.from(list || []).slice(0, MAX_FILES - files.length).map(file => ({
      id: crypto.randomUUID(), file, url: file.type.startsWith('image/') ? URL.createObjectURL(file) : null
    }));
    setFiles([...files, ...added]);
  };

  const dropFiles = (kept) => {
    files.filter(f => !kept.includes(f) && f.url).forEach(f => URL.revokeObjectURL(f.url));
    setFiles(kept);
  };

  const handleScan = async () => {
    setScanning(true);
    setMessage(null);
    try {
      const prepared = await Promise.all(files.map(f => prepareReceiptFile(f.file)));
      const result = await scanReceipt(prepared);
      setDraft({
        storeName: result.storeName || '',
        purchaseDate: result.purchaseDate || new Date().toISOString().slice(0, 10),
        total: result.total ?? '',
        warnings: result.warnings || '',
        items: (result.items || []).map(i => ({ ...i, key: crypto.randomUUID() })),
      });
    } catch (err) {
      setMessage({ text: err.message || 'Lettura non riuscita', type: 'error' });
    } finally {
      setScanning(false);
    }
  };

  // Cambiando quantita', prezzo o sconto l'importo pagato si ricalcola (quantita' x prezzo - sconto)
  const setItem = (key, field, value) =>
    setDraft(d => ({
      ...d, items: d.items.map(i => {
        if (i.key !== key) return i;
        const next = { ...i, [field]: value };
        if (['quantity', 'unitPrice', 'discount'].includes(field) && num(next.unitPrice) > 0) {
          next.totalPrice = (Math.round((num(next.quantity || 1) * num(next.unitPrice) - num(next.discount)) * 100) / 100).toFixed(2);
        }
        return next;
      })
    }));
  const removeItem = (key) => setDraft(d => ({ ...d, items: d.items.filter(i => i.key !== key) }));
  const addItem = () => setDraft(d => ({
    ...d,
    items: [...d.items, {
      key: crypto.randomUUID(), rawName: '', name: '', foodCategory: 'Altro',
      quantity: 1, unit: 'pz', unitPrice: '', discount: '', totalPrice: ''
    }]
  }));

  const itemsSum = useMemo(
    () => (draft ? draft.items.reduce((s, i) => s + num(i.totalPrice), 0) : 0), [draft]);
  const mismatch = draft ? Math.abs(itemsSum - num(draft.total)) > 0.01 : false;

  const handleSave = async () => {
    setSaving(true);
    setMessage(null);
    try {
      await saveReceipt({
        storeName: draft.storeName,
        purchaseDate: draft.purchaseDate,
        total: num(draft.total),
        categoryId: categoryId || null,
        linkTransactionId: linkId || null,
        items: draft.items.filter(i => i.name.trim()).map(({ key, ...i }) => ({ // eslint-disable-line no-unused-vars
          ...i,
          quantity: num(i.quantity) || 1,
          unitPrice: num(i.unitPrice),
          discount: num(i.discount) > 0 ? num(i.discount) : null,
          totalPrice: num(i.totalPrice),
        })),
      });
      setMessage({
        text: linkId
          ? 'Scontrino salvato e collegato al movimento esistente: la spesa non è stata contata due volte.'
          : 'Scontrino salvato: la spesa è stata aggiunta alle transazioni.',
        type: 'success'
      });
      setDraft(null);
      dropFiles([]);
      onSaved();
    } catch (err) {
      setMessage({ text: err.message || 'Salvataggio non riuscito', type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  if (!draft) {
    return (
      <div className="glass-panel" style={{ padding: '1.5rem' }}>
        <h3 style={{ marginTop: 0 }}>Carica lo scontrino</h3>
        <p className="text-muted text-sm">
          Appoggia lo scontrino su una superficie piana, con buona luce e il testo ben leggibile. Se è lungo, scatta più
          foto dall'alto verso il basso (fino a {MAX_FILES}). Dopo la lettura potrai controllare e correggere tutto prima di salvare.
        </p>

        {/* "capture" apre direttamente la fotocamera sul telefono; sul computer apre la scelta file */}
        <input ref={cameraRef} type="file" accept="image/*" capture="environment" style={{ display: 'none' }}
          onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />
        <input ref={galleryRef} type="file" multiple accept="image/*,application/pdf" style={{ display: 'none' }}
          onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />

        <div className="scan-actions">
          <button type="button" className="btn btn-primary scan-camera" disabled={files.length >= MAX_FILES}
            onClick={() => cameraRef.current.click()}>
            <Camera size={22} /> {files.length ? 'Aggiungi un\'altra foto' : 'Scatta una foto'}
          </button>
          <button type="button" className="btn btn-glass" disabled={files.length >= MAX_FILES}
            onClick={() => galleryRef.current.click()}>
            <ImageIcon size={20} /> Galleria o PDF
          </button>
        </div>

        {files.length > 0 && (
          <div className="scan-thumbs">
            {files.map((f, idx) => (
              <div key={f.id} className="scan-thumb">
                {f.url ? <img src={f.url} alt={`Foto ${idx + 1}`} /> : (
                  <div className="scan-thumb-doc"><FileText size={28} /><span>{f.file.name}</span></div>
                )}
                <span className="scan-thumb-n">{idx + 1}</span>
                <button type="button" aria-label={`Rimuovi la foto ${idx + 1}`} className="scan-thumb-x"
                  onClick={() => dropFiles(files.filter(x => x !== f))}>
                  <X size={16} />
                </button>
              </div>
            ))}
          </div>
        )}

        <button className="btn btn-primary scan-go" disabled={files.length === 0 || scanning} onClick={handleScan}>
          <ScanLine size={20} /> {scanning ? 'Lettura in corso…' : 'Leggi scontrino'}
        </button>
      </div>
    );
  }

  const cell = { padding: '0.35rem' };
  const compact = { padding: '0.55rem 0.6rem', minWidth: 0 };
  const radio = { width: 'auto', flex: '0 0 auto', margin: 0, padding: 0 }; // gli input hanno uno stile globale a tutta larghezza
  return (
    <div className="glass-panel" style={{ padding: '2rem' }}>
      <h3 style={{ marginTop: 0 }}>Controlla i dati letti</h3>

      {draft.warnings && (
        <div style={{ display: 'flex', gap: '0.5rem', color: 'var(--danger)', marginBottom: '1rem' }}>
          <AlertTriangle size={18} /> <span>{draft.warnings}</span>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 200px), 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <label>Negozio
          <input className="form-input" value={draft.storeName}
            onChange={(e) => setDraft({ ...draft, storeName: e.target.value })} />
        </label>
        <label>Data
          <input type="date" className="form-input" value={draft.purchaseDate}
            onChange={(e) => setDraft({ ...draft, purchaseDate: e.target.value })} />
        </label>
        <label>Totale pagato (€)
          <input type="number" step="0.01" className="form-input" value={draft.total}
            onChange={(e) => setDraft({ ...draft, total: e.target.value })} />
        </label>
        <label>{linkId ? 'Categoria (se il movimento non ne ha una)' : 'Categoria della spesa'}
          <select className="form-select" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="">— Nessuna —</option>
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
      </div>

      {matches.length > 0 && (
        <div className="glass-panel" style={{ padding: '1rem 1.25rem', marginBottom: '1.5rem', borderLeft: '4px solid var(--accent-primary)' }}>
          <strong>Questo acquisto sembra già nei tuoi movimenti</strong>
          <p className="text-muted text-sm" style={{ margin: '0.3rem 0 0.75rem' }}>
            Per non contarlo due volte puoi collegare lo scontrino al movimento esistente invece di creare una nuova spesa.
          </p>
          {matches.map(m => (
            <label key={m.id} style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', marginBottom: '0.4rem', cursor: 'pointer' }}>
              <input type="radio" name="link" style={radio} checked={linkId === m.id} onChange={() => setLinkId(m.id)} />
              <span>Collega a: {m.description} · <PrivacyMask>{eur(m.amount)}</PrivacyMask> · {day(m.transactionDate)}</span>
            </label>
          ))}
          <label style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', cursor: 'pointer' }}>
            <input type="radio" name="link" style={radio} checked={linkId === ''} onChange={() => setLinkId('')} />
            <span>Crea una nuova spesa (è un altro acquisto)</span>
          </label>
        </div>
      )}

      <div style={{ overflowX: 'auto' }}>
        <table className="receipt-items" style={{ width: '100%', borderCollapse: 'collapse', minWidth: '900px' }}>
          <thead>
            <tr style={{ textAlign: 'left' }}>
              <th>Prodotto</th><th>Tipo</th><th style={{ width: '95px' }}>Qtà</th>
              <th style={{ width: '105px' }}>Unità</th><th style={{ width: '105px' }}>Prezzo unit.</th>
              <th style={{ width: '95px' }}>Sconto</th><th style={{ width: '105px' }}>Pagato</th><th style={{ width: '40px' }} />
            </tr>
          </thead>
          <tbody>
            {draft.items.map(i => (
              <tr key={i.key} title={i.rawName ? `Sullo scontrino: ${i.rawName}` : ''}>
                <td className="cell-name" data-label="Prodotto" style={cell}><input className="form-input" style={compact} value={i.name}
                  onChange={(e) => setItem(i.key, 'name', e.target.value)} /></td>
                <td className="cell-type" data-label="Tipo" style={cell}>
                  <select className="form-select" style={compact} value={i.foodCategory || 'Altro'}
                    onChange={(e) => setItem(i.key, 'foodCategory', e.target.value)}>
                    {FOOD_CATEGORIES.map(c => <option key={c}>{c}</option>)}
                  </select>
                </td>
                <td data-label="Quantità" style={cell}><input type="number" step="0.001" className="form-input" style={compact} value={i.quantity ?? ''}
                  onChange={(e) => setItem(i.key, 'quantity', e.target.value)} /></td>
                <td data-label="Unità" style={cell}>
                  <select className="form-select" style={compact} value={i.unit || 'pz'}
                    onChange={(e) => setItem(i.key, 'unit', e.target.value)}>
                    <option>pz</option><option>kg</option><option>l</option>
                  </select>
                </td>
                <td data-label="Prezzo unit." style={cell}><input type="number" step="0.01" className="form-input" style={compact} value={i.unitPrice ?? ''}
                  onChange={(e) => setItem(i.key, 'unitPrice', e.target.value)} /></td>
                <td data-label="Sconto" style={cell}><input type="number" step="0.01" min="0" className="form-input" style={compact} value={i.discount || ''}
                  placeholder="0" onChange={(e) => setItem(i.key, 'discount', e.target.value)} /></td>
                <td data-label="Pagato" style={cell}><input type="number" step="0.01" className="form-input" style={compact} value={i.totalPrice ?? ''}
                  onChange={(e) => setItem(i.key, 'totalPrice', e.target.value)} /></td>
                <td className="cell-del" style={cell}>
                  <button className="btn-icon" aria-label="Rimuovi riga" onClick={() => removeItem(i.key)}
                    style={{ color: 'var(--danger)', background: 'none', border: 'none', cursor: 'pointer' }}>
                    <Trash2 size={18} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1rem', flexWrap: 'wrap', gap: '1rem' }}>
        <button className="btn btn-glass" onClick={addItem}><Plus size={18} /> Aggiungi riga</button>
        <div style={{ color: mismatch ? 'var(--danger)' : 'var(--text-muted)' }}>
          Somma righe: <strong>{eur(itemsSum)}</strong> · Totale: <strong>{eur(num(draft.total))}</strong>
          {mismatch && ' — non coincidono: controlla le righe'}
        </div>
      </div>

      <div style={{ display: 'flex', gap: '1rem', marginTop: '1.5rem' }}>
        <button className="btn btn-primary" disabled={saving || draft.items.length === 0} onClick={handleSave}>
          {saving ? 'Salvataggio…' : 'Salva scontrino'}
        </button>
        <button className="btn btn-glass" onClick={() => setDraft(null)}>Annulla</button>
      </div>
    </div>
  );
};

// ─── Elenco scontrini ──────────────────────────────────────────────────────

const ReceiptsTab = ({ setMessage }) => {
  const [receipts, setReceipts] = useState([]);
  const [open, setOpen] = useState({}); // id → righe

  const load = useCallback(() => {
    fetchReceipts().then(setReceipts).catch(() => setMessage({ text: 'Impossibile caricare gli scontrini', type: 'error' }));
  }, [setMessage]);
  useEffect(() => { load(); }, [load]);

  const toggle = async (id) => {
    if (open[id]) {
      setOpen(o => { const rest = { ...o }; delete rest[id]; return rest; });
      return;
    }
    const detail = await fetchReceipt(id);
    setOpen(o => ({ ...o, [id]: detail.items }));
  };

  const remove = async (r) => {
    const note = r.transactionCreated === false
      ? 'Il movimento in banca collegato resterà.'
      : 'Verrà eliminata anche la spesa collegata.';
    if (!window.confirm(`Eliminare lo scontrino ${r.storeName} del ${day(r.purchaseDate)}? ${note}`)) return;
    await deleteReceipt(r.id);
    load();
  };

  if (receipts.length === 0) {
    return <div className="glass-panel" style={{ padding: '2rem' }}>Nessuno scontrino salvato.</div>;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      {receipts.map(r => (
        <div key={r.id} className="glass-panel" style={{ padding: '1rem 1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem' }}>
            <button onClick={() => toggle(r.id)} style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', display: 'flex', gap: '0.75rem', alignItems: 'center', textAlign: 'left' }}>
              {open[r.id] ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
              <span>
                <strong>{r.storeName}</strong> <span className="text-muted">· {day(r.purchaseDate)}</span>
                {r.transactionCreated === false && <span className="text-muted text-sm"> · collegato al movimento in banca</span>}
              </span>
            </button>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <strong><PrivacyMask>{eur(r.total)}</PrivacyMask></strong>
              <button aria-label="Elimina scontrino" onClick={() => remove(r)}
                style={{ color: 'var(--danger)', background: 'none', border: 'none', cursor: 'pointer' }}>
                <Trash2 size={18} />
              </button>
            </div>
          </div>
          {open[r.id] && (
            <table style={{ width: '100%', marginTop: '1rem', borderCollapse: 'collapse' }}>
              <tbody>
                {open[r.id].map(i => (
                  <tr key={i.id}>
                    <td style={{ padding: '0.3rem 0' }}>{i.name}</td>
                    <td className="text-muted text-sm">{i.foodCategory}</td>
                    <td className="text-muted text-sm">
                      {Number(i.quantity)} {i.unit} × {eur(i.unitPrice)}
                      {Number(i.discount) > 0 && (
                        <span style={{ color: 'var(--success)', marginLeft: '0.5rem' }}>
                          sconto -{eur(i.discount)}{Number(i.quantity) > 1 && ` · ${eur(Number(i.totalPrice) / Number(i.quantity))}/${i.unit}`}
                        </span>
                      )}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {Number(i.discount) > 0 && (
                        <span className="text-muted text-sm" style={{ textDecoration: 'line-through', marginRight: '0.5rem' }}>
                          <PrivacyMask>{eur(Number(i.totalPrice) + Number(i.discount))}</PrivacyMask>
                        </span>
                      )}
                      <PrivacyMask>{eur(i.totalPrice)}</PrivacyMask>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ))}
    </div>
  );
};

// ─── Prodotti e storico prezzi ─────────────────────────────────────────────

const ProductsTab = () => {
  const [products, setProducts] = useState([]);
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(null);
  const [history, setHistory] = useState([]);

  useEffect(() => { fetchProducts().then(setProducts).catch(() => { }); }, []);

  const select = async (p) => {
    setSelected(p);
    setHistory((await fetchProductHistory(p.name, p.unit)).map(h => ({ ...h, unitPrice: Number(h.unitPrice), label: day(h.date) })));
  };

  const shown = products.filter(p => p.name.toLowerCase().includes(search.toLowerCase()));

  if (products.length === 0) {
    return <div className="glass-panel" style={{ padding: '2rem' }}>Nessun prodotto ancora: salva il primo scontrino.</div>;
  }
  return (
    <div className="glass-panel" style={{ padding: '1.5rem' }}>
      <input className="form-input" placeholder="Cerca un prodotto…" value={search}
        onChange={(e) => setSearch(e.target.value)} style={{ marginBottom: '0.5rem', maxWidth: '360px' }} />
      <p className="text-muted text-sm" style={{ marginBottom: '1rem' }}>Prezzi per unità effettivamente pagati, sconti compresi.</p>
      <div style={{ overflowX: 'auto' }}>
        <table className="products-table" style={{ width: '100%', borderCollapse: 'collapse', minWidth: '760px' }}>
          <thead>
            <tr style={{ textAlign: 'left' }}>
              <th>Prodotto</th><th className="col-extra">Acquisti</th><th>Ultimo</th><th>Minimo</th><th className="col-extra">Medio</th><th className="col-extra">Massimo</th><th>Più conveniente</th>
            </tr>
          </thead>
          <tbody>
            {shown.map(p => (
              <tr key={`${p.name}|${p.unit}`} onClick={() => select(p)}
                style={{ cursor: 'pointer', background: selected?.name === p.name && selected?.unit === p.unit ? 'rgba(255,255,255,0.06)' : undefined }}>
                <td style={{ padding: '0.45rem 0' }}>{p.name} <span className="text-muted text-sm">/{p.unit}</span></td>
                <td className="col-extra">{p.purchases}</td>
                <td><PrivacyMask>{eur(p.lastUnitPrice)}</PrivacyMask></td>
                <td><PrivacyMask>{eur(p.minUnitPrice)}</PrivacyMask></td>
                <td className="col-extra"><PrivacyMask>{eur(p.avgUnitPrice)}</PrivacyMask></td>
                <td className="col-extra"><PrivacyMask>{eur(p.maxUnitPrice)}</PrivacyMask></td>
                <td>{p.cheapestStore}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selected && (
        <div style={{ marginTop: '2rem' }}>
          <h4 style={{ marginBottom: '1rem' }}>Prezzo di «{selected.name}» nel tempo (€ / {selected.unit})</h4>
          {history.length < 2 ? (
            <p className="text-muted text-sm">Serve almeno un secondo acquisto per vedere l'andamento.</p>
          ) : (
            <div style={{ height: 260 }}>
              <ResponsiveContainer>
                <LineChart data={history}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                  <XAxis dataKey="label" stroke="var(--text-muted)" />
                  <YAxis stroke="var(--text-muted)" domain={['auto', 'auto']} />
                  <Tooltip formatter={(v) => eur(v)} labelFormatter={(l, items) => `${l} · ${items?.[0]?.payload?.store ?? ''}`} />
                  <Line type="monotone" dataKey="unitPrice" stroke="var(--accent-primary)" strokeWidth={2} dot />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ─── Spesa per tipo di alimento ────────────────────────────────────────────

const SpendingTab = () => {
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7)); // yyyy-MM
  const [rows, setRows] = useState([]);

  useEffect(() => {
    const [y, m] = month.split('-').map(Number);
    const last = new Date(y, m, 0).getDate();
    fetchFoodSpending(`${month}-01`, `${month}-${String(last).padStart(2, '0')}`).then(setRows).catch(() => setRows([]));
  }, [month]);

  const total = rows.reduce((s, r) => s + Number(r.total), 0);

  return (
    <div className="glass-panel" style={{ padding: '2rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.5rem' }}>
        <input type="month" className="form-input" value={month} onChange={(e) => setMonth(e.target.value)} style={{ maxWidth: '200px' }} />
        <div>Totale spesa alimentare: <strong><PrivacyMask>{eur(total)}</PrivacyMask></strong></div>
      </div>
      {rows.length === 0 ? (
        <p className="text-muted">Nessuno scontrino in questo mese.</p>
      ) : rows.map(r => (
        <div key={r.foodCategory} style={{ marginBottom: '0.9rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>{r.foodCategory}</span>
            <span><PrivacyMask>{eur(r.total)}</PrivacyMask> <span className="text-muted text-sm">({total > 0 ? Math.round((Number(r.total) / total) * 100) : 0}%)</span></span>
          </div>
          <div style={{ height: 8, background: 'rgba(255,255,255,0.08)', borderRadius: 4, marginTop: 4 }}>
            <div style={{ height: '100%', width: `${total > 0 ? Math.max(0, (Number(r.total) / total) * 100) : 0}%`, background: 'var(--accent-gradient)', borderRadius: 4 }} />
          </div>
        </div>
      ))}
    </div>
  );
};

export default Groceries;
