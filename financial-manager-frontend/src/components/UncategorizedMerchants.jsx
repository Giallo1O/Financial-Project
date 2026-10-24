import React, { useState, useEffect, useCallback } from 'react';
import { Wand2, Check } from 'lucide-react';
import { fetchUncategorizedMerchants, saveCategoryRule, applyCategoryRules } from '../api';
import { eur } from '../utils/format';
import PrivacyMask from './PrivacyMask';

/**
 * Movimenti già salvati senza categoria, raggruppati per esercente. "Assegna" crea la regola per la parola chiave
 * e la applica subito: categorizza tutti i movimenti di quell'esercente, e quelli dei prossimi import.
 */
const UncategorizedMerchants = ({ categories, onRulesChanged }) => {
  const [groups, setGroups] = useState([]);
  const [choice, setChoice] = useState({}); // chiave gruppo -> { keyword, categoryId }
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    fetchUncategorizedMerchants().then(setGroups).catch(() => setMessage('Impossibile caricare i movimenti senza categoria'));
  }, []);
  useEffect(() => { load(); }, [load, categories]);

  const keyOf = (g) => `${g.keyword}|${g.type}`;
  const optionsFor = (g) => categories.filter(c => c.expense === (g.type === 'USCITA'));
  const set = (g, field, value) => setChoice(prev => ({ ...prev, [keyOf(g)]: { ...prev[keyOf(g)], [field]: value } }));

  const applyRules = async () => {
    setBusy(true);
    try {
      const { updated } = await applyCategoryRules();
      setMessage(updated > 0 ? `Categorizzati ${updated} movimenti con le regole.` : 'Nessun movimento corrisponde alle regole.');
      load();
    } catch (err) {
      setMessage(err.message);
    } finally {
      setBusy(false);
    }
  };

  const assign = async (g) => {
    const c = choice[keyOf(g)] || {};
    const keyword = (c.keyword ?? g.keyword).trim();
    const categoryId = c.categoryId || optionsFor(g)[0]?.id;
    if (!categoryId) return;
    setBusy(true);
    try {
      await saveCategoryRule(keyword, categoryId);
      const { updated } = await applyCategoryRules();
      setMessage(`Regola «${keyword}» creata: categorizzati ${updated} movimenti.`);
      onRulesChanged?.();
      load();
    } catch (err) {
      setMessage(err.message);
    } finally {
      setBusy(false);
    }
  };

  const total = groups.reduce((s, g) => s + g.count, 0);

  return (
    <section className="glass-panel" style={{ padding: '1.5rem', marginTop: '2rem' }}>
      <h3 className="section-title" style={{ marginBottom: '0.5rem' }}>Movimenti senza categoria</h3>
      <p className="text-muted text-sm" style={{ marginBottom: '1.25rem' }}>
        {total > 0
          ? `${total} movimenti senza categoria, raggruppati per esercente. Scegli la categoria e premi Assegna: viene creata la regola e applicata a tutti i suoi movimenti, anche nei prossimi import. Puoi correggere la parola chiave prima di assegnare.`
          : 'Tutti i movimenti hanno una categoria.'}
      </p>

      <button type="button" className="btn btn-glass" onClick={applyRules} disabled={busy} style={{ marginBottom: '1rem' }}>
        <Wand2 size={18} /> Applica le regole ai movimenti senza categoria
      </button>
      {message && <p className="text-sm" style={{ marginBottom: '1rem' }}>{message}</p>}

      {groups.length > 0 && (
        <div className="merchant-groups">
          {groups.map(g => {
            const options = optionsFor(g);
            const c = choice[keyOf(g)] || {};
            const keyword = c.keyword ?? g.keyword;
            return (
              <div key={keyOf(g)} className="merchant-group">
                <div className="merchant-info">
                  <input className="form-input" value={keyword} aria-label="Parola chiave"
                    onChange={(e) => set(g, 'keyword', e.target.value)} style={{ fontWeight: 600 }} />
                  <span className="text-muted text-sm">
                    {g.count} {g.count === 1 ? 'movimento' : 'movimenti'} · {g.type === 'USCITA' ? 'uscite' : 'entrate'}{' '}
                    <PrivacyMask>{eur(g.total)}</PrivacyMask> · es. {g.examples.join(', ')}
                  </span>
                </div>
                <select className="form-select" value={c.categoryId || options[0]?.id || ''} aria-label="Categoria"
                  onChange={(e) => set(g, 'categoryId', e.target.value)} disabled={options.length === 0}>
                  {options.length === 0 && (
                    <option value="">Crea prima una categoria di {g.type === 'USCITA' ? 'uscita' : 'entrata'}</option>
                  )}
                  {options.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
                </select>
                <button type="button" className="btn btn-primary" onClick={() => assign(g)}
                  disabled={busy || options.length === 0 || keyword.trim().length < 3}>
                  <Check size={18} /> Assegna
                </button>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
};

export default UncategorizedMerchants;
