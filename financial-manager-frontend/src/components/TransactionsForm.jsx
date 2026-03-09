import React, { useState, useEffect } from 'react';
import { PlusCircle, MinusCircle, Tag, Calendar, FileText, DollarSign } from 'lucide-react';
import { fetchCategories } from '../api';
import './Components.css';

const TransactionsForm = ({ onAddTransaction }) => {
  const [activeTab, setActiveTab] = useState('uscita'); // 'uscita' | 'entrata'
  const [amount, setAmount] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [date, setDate] = useState('');
  const [description, setDescription] = useState('');
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
      date,
      description,
    });

    // Reset form
    setAmount('');
    setCategoryId('');
    setDate('');
    setDescription('');
  };

  return (
    <div className="glass-panel form-card">
      <div className="tabs">
        <button
          className={`tab-btn ${activeTab === 'uscita' ? 'active-expense' : ''}`}
          onClick={() => { setActiveTab('uscita'); setCategoryId(''); }}
        >
          <MinusCircle size={18} />
          Add Expense
        </button>
        <button
          className={`tab-btn ${activeTab === 'entrata' ? 'active-income' : ''}`}
          onClick={() => { setActiveTab('entrata'); setCategoryId(''); }}
        >
          <PlusCircle size={18} />
          Add Income
        </button>
      </div>

      <form onSubmit={handleSubmit} className="transaction-form">
        <div className="form-group row">
          <div className="input-group full-width">
            <label>Amount</label>
            <div className="input-wrapper">
              <DollarSign size={18} className="input-icon" />
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
              />
            </div>
          </div>
        </div>

        <div className="form-group row">
          <div className="input-group half-width">
            <label>Category</label>
            <div className="input-wrapper">
              <Tag size={18} className="input-icon" />
              <select
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
                required
                disabled={loadingCats}
              >
                <option value="" disabled>
                  {loadingCats ? 'Loading...' : 'Select...'}
                </option>
                {categories.map(cat => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="input-group half-width">
            <label>Date</label>
            <div className="input-wrapper">
              <Calendar size={18} className="input-icon" />
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                required
              />
            </div>
          </div>
        </div>

        <div className="form-group">
          <label>Description (Optional)</label>
          <div className="input-wrapper">
            <FileText size={18} className="input-icon" />
            <input
              type="text"
              placeholder="What was this for?"
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
          {activeTab === 'uscita' ? 'Save Expense' : 'Save Income'}
        </button>
      </form>
    </div>
  );
};

export default TransactionsForm;
