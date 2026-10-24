import React, { useState, useEffect, useCallback } from 'react';
import { Sparkles, Plus } from 'lucide-react';
import { fetchRecurringSuggestions, createRecurringTransaction } from '../api';
import { eur } from '../utils/format';
import PrivacyMask from './PrivacyMask';

/**
 * Spese ed entrate che si ripetono ogni mese nei movimenti e non hanno ancora una ricorrenza.
 * "Aggiungi" crea la ricorrenza già compilata (conferma manuale: la si può cambiare dopo).
 */
const RecurringSuggestions = ({ categories, onAdded }) => {
  const [suggestions, setSuggestions] = useState([]);
  const [busy, setBusy] = useState(null);
  const [message, setMessage] = useState(null);

  const load = useCallback(() => {
    fetchRecurringSuggestions().then(setSuggestions).catch(() => setSuggestions([]));
  }, []);
  useEffect(() => { load(); }, [load]);

  const categoryName = (id) => categories.find(c => c.id === id)?.name;

  const add = async (s) => {
    setBusy(s.keyword);
    try {
      await createRecurringTransaction({
        description: s.description,
        amount: s.amount,
        type: s.type,
        categoryId: s.categoryId,
        dayOfMonth: s.dayOfMonth,
        autoConfirm: false,
        recurrenceInterval: 'MONTHLY',
      });
      setMessage(`Ricorrenza «${s.description}» aggiunta.`);
      load();
      onAdded?.();
    } catch (err) {
      setMessage(err.message);
    } finally {
      setBusy(null);
    }
  };

  if (suggestions.length === 0 && !message) return null;

  return (
    <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: 'var(--radius-lg)', marginBottom: '2rem' }}>
      <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
        <Sparkles size={20} color="var(--accent-secondary)" /> Ricorrenze trovate nei tuoi movimenti
      </h3>
      <p className="text-muted text-sm" style={{ marginBottom: '1rem' }}>
        Pagamenti che si ripetono ogni mese e non hai ancora tra le ricorrenze. Quando importi l'estratto conto, il movimento
        della banca viene riconosciuto come la ricorrenza (entro 5 giorni dalla scadenza) e non viene contato due volte;
        per le bollette con importo variabile prende l'importo vero.
      </p>
      {message && <p className="text-sm" style={{ marginBottom: '0.75rem' }}>{message}</p>}
      <div className="merchant-groups">
        {suggestions.map(s => (
          <div key={`${s.keyword}|${s.type}`} className="merchant-group" style={{ gridTemplateColumns: 'minmax(0, 1fr) auto' }}>
            <div className="merchant-info">
              <strong>{s.description}</strong>
              <span className="text-muted text-sm">
                {s.type === 'USCITA' ? 'Uscita' : 'Entrata'} di{' '}
                <PrivacyMask>{s.variableAmount ? `circa ${eur(s.amount)}` : eur(s.amount)}</PrivacyMask>
                {s.variableAmount && ' (importo variabile, media degli ultimi 3)'} · intorno al giorno {s.dayOfMonth} ·
                trovata in {s.months} mesi
                {categoryName(s.categoryId) && ` · ${categoryName(s.categoryId)}`}
              </span>
            </div>
            <button type="button" className="btn btn-primary" disabled={busy !== null} onClick={() => add(s)}>
              <Plus size={18} /> Aggiungi
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default RecurringSuggestions;
