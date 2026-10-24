import React, { useState, useEffect } from 'react';
import { PlusCircle, MinusCircle, Tag, Calendar, FileText, Euro } from 'lucide-react';
import { fetchCategories } from '../api';
import AccountSelect from './AccountSelect';
import DatePicker, { registerLocale } from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import { format } from 'date-fns';
import { it } from 'date-fns/locale/it';
import './Components.css';

registerLocale('it', it);

const TransactionsForm = ({ onAddTransaction, editing, onCancelEdit }) => {
  // In modifica il form parte dal movimento scelto (la pagina lo rimonta con una key diversa)
  const [activeTab, setActiveTab] = useState(editing?.type === 'entrata' ? 'entrata' : 'uscita'); // 'uscita' | 'entrata'
  const [amount, setAmount] = useState(editing ? String(editing.amount) : '');
  const [categoryId, setCategoryId] = useState(editing?.categoryId || '');
  const [date, setDate] = useState(editing?.date ? new Date(`${editing.date}T00:00:00`) : new Date());
  const [description, setDescription] = useState(editing?.description || '');
  const [accountId, setAccountId] = useState(editing?.accountId || '');
  const [categories, setCategories] = useState([]);
  const [loadingCats, setLoadingCats] = useState(true);

  useEffect(() => {
    fetchCategories()
      .then(setCategories)
      .finally(() => setLoadingCats(false));
  }, []);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!amount || !categoryId || !date) return;

    onAddTransaction({
      type: activeTab,
      amount: parseFloat(amount),
      categoryId,
      date: format(date, 'yyyy-MM-dd'),
      description,
      accountId: accountId || null,
    });

    // Reset form (keep date)
    setAmount('');
    setCategoryId('');
    setDescription('');
  };

  return (
    <div className="glass-panel form-card">
      <h3 className="section-title" style={{ marginBottom: '1.5rem', color: 'var(--text-primary)' }}>
        {editing ? 'Modifica movimento' : 'Nuova Transazione'}
      </h3>
      
      <div className="tabs">
        <button
          className={`tab-btn ${activeTab === 'uscita' ? 'active-expense' : ''}`}
          onClick={() => { setActiveTab('uscita'); setCategoryId(''); }}
          type="button"
        >
          <MinusCircle size={22} />
          Uscita
        </button>
        <button
          className={`tab-btn ${activeTab === 'entrata' ? 'active-income' : ''}`}
          onClick={() => { setActiveTab('entrata'); setCategoryId(''); }}
          type="button"
        >
          <PlusCircle size={22} />
          Entrata
        </button>
      </div>

      <form onSubmit={handleSubmit} className="transaction-form">
        <div className="form-group">
          <label>Importo</label>
          <div className="input-wrapper">
            <Euro size={18} className="input-icon" />
            <input
              type="number"
              step="0.01"
              min="0.01"
              max="1000000"
              placeholder="0.00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
            />
          </div>
        </div>

        <div className="form-group row">
          <div className="input-group half-width">
            <label>Categoria</label>
            <div className="input-wrapper">
              <Tag size={18} className="input-icon" />
              <select
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
                required
                disabled={loadingCats}
              >
                <option value="" disabled>
                  {loadingCats ? 'Caricamento...' : 'Seleziona...'}
                </option>
                {categories
                  .filter(cat => activeTab === 'uscita' ? cat.expense : !cat.expense)
                  .map(cat => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name}
                    </option>
                  ))}
              </select>
            </div>
          </div>

          <div className="input-group half-width">
            <label>Data</label>
            <div className="input-wrapper date-picker-wrapper">
              <Calendar size={18} className="input-icon" />
              <DatePicker
                selected={date}
                onChange={(date) => setDate(date)}
                dateFormat="dd/MM/yyyy"
                locale="it"
                className="form-input date-input"
                required
              />
            </div>
          </div>
        </div>

        <div className="form-group">
          <AccountSelect value={accountId} onChange={setAccountId} useLast={!editing} />
        </div>

        <div className="form-group">
          <label>Descrizione (Opzionale)</label>
          <div className="input-wrapper">
            <FileText size={18} className="input-icon" />
            <input
              type="text"
              placeholder="Per cosa è stato speso?"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
        </div>

        <button
          type="submit"
          className={`btn ${activeTab === 'uscita' ? 'btn-danger' : 'btn-success'} transaction-submit-btn`}
          disabled={loadingCats}
        >
          {editing ? 'Salva modifiche' : activeTab === 'uscita' ? 'Salva Uscita' : 'Salva Entrata'}
        </button>
        {editing && (
          <button type="button" className="btn" style={{ marginTop: '0.75rem', width: '100%' }}
            onClick={onCancelEdit}>
            Annulla
          </button>
        )}
      </form>
    </div>
  );
};

export default TransactionsForm;
