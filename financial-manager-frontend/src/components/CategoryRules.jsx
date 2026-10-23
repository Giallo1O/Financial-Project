import React, { useState, useEffect, useCallback } from 'react';
import { Wand2, Trash2, Plus } from 'lucide-react';
import {
  fetchCategoryRules, saveCategoryRule, deleteCategoryRule, learnCategoryRulesFromHistory
} from '../api';

/** Regole "parola chiave → categoria" usate per proporre la categoria quando importi un estratto conto. */
const CategoryRules = ({ categories }) => {
  const [rules, setRules] = useState([]);
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [message, setMessage] = useState(null);

  const load = useCallback(() => {
    fetchCategoryRules().then(setRules).catch(() => setMessage('Impossibile caricare le regole'));
  }, []);
  useEffect(() => { load(); }, [load, categories]);

  const byId = Object.fromEntries(categories.map(c => [c.id, c]));

  const add = async (e) => {
    e.preventDefault();
    setMessage(null);
    try {
      await saveCategoryRule(keyword, categoryId || categories[0]?.id);
      setKeyword('');
      load();
    } catch (err) {
      setMessage(err.message);
    }
  };

  const remove = async (id) => {
    await deleteCategoryRule(id);
    load();
  };

  const learnFromHistory = async () => {
    const { created } = await learnCategoryRulesFromHistory();
    setMessage(created > 0 ? `Create ${created} regole dalle tue transazioni.` : 'Nessuna nuova regola da imparare dalle transazioni.');
    load();
  };

  return (
    <section className="glass-panel" style={{ padding: '1.5rem', marginTop: '2rem' }}>
      <h3 className="section-title" style={{ marginBottom: '0.5rem' }}>Regole automatiche</h3>
      <p className="text-muted text-sm" style={{ marginBottom: '1.25rem' }}>
        Quando importi un estratto conto, se la descrizione contiene la parola chiave la categoria viene proposta da sola
        (vince la regola più specifica). Le regole si creano anche scegliendo le categorie durante l'import.
      </p>

      <form onSubmit={add} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '1.25rem' }}>
        <input className="form-input" style={{ flex: '1 1 200px' }} placeholder="Parola chiave, es. esselunga" value={keyword}
          onChange={(e) => setKeyword(e.target.value)} required />
        <select className="form-select" style={{ flex: '1 1 200px' }} value={categoryId || categories[0]?.id || ''}
          onChange={(e) => setCategoryId(e.target.value)}>
          {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <button type="submit" className="btn btn-primary" disabled={categories.length === 0}><Plus size={18} /> Aggiungi</button>
        <button type="button" className="btn btn-glass" onClick={learnFromHistory}>
          <Wand2 size={18} /> Crea dalle mie transazioni
        </button>
      </form>

      {message && <p className="text-sm" style={{ marginBottom: '1rem' }}>{message}</p>}

      {rules.length === 0 ? (
        <p className="text-muted text-sm">Nessuna regola ancora.</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <tbody>
            {[...rules].sort((a, b) => a.keyword.localeCompare(b.keyword)).map(r => (
              <tr key={r.id}>
                <td style={{ padding: '0.4rem 0' }}><strong>{r.keyword}</strong></td>
                <td>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}>
                    <span style={{ width: 10, height: 10, borderRadius: '50%', background: byId[r.categoryId]?.color || '#6b7280' }} />
                    {byId[r.categoryId]?.name || '—'}
                  </span>
                </td>
                <td style={{ textAlign: 'right' }}>
                  <button aria-label={`Elimina la regola ${r.keyword}`} onClick={() => remove(r.id)}
                    style={{ color: 'var(--danger)', background: 'none', border: 'none', cursor: 'pointer' }}>
                    <Trash2 size={18} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
};

export default CategoryRules;
