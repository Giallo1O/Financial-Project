import React, { useState, useEffect, useMemo } from 'react';
import {
  CalendarClock, Plus, CheckCircle, Clock, AlertCircle,
  Calendar as CalendarIcon, List, Play, Pencil, Trash2,
  ChevronLeft, ChevronRight, Check, Zap, ArrowUpRight, ArrowDownLeft
} from 'lucide-react';
import {
  fetchRecurringTransactions, createRecurringTransaction,
  updateRecurringTransaction, deleteRecurringTransaction,
  confirmRecurringTransaction, processAutoRecurringTransactions,
  fetchCategories
} from '../api';
import AccountSelect from '../components/AccountSelect';
import '../components/Components.css';

const RecurringTransactions = () => {
  const [recurringList, setRecurringList] = useState([]);
  const [categories, setCategories] = useState([]);
  const [activeTab, setActiveTab] = useState('calendar'); // 'calendar' or 'list'

  // Calendar view state
  const [selectedDate, setSelectedDate] = useState(new Date());

  // Modal State
  const [showModal, setShowModal] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [type, setType] = useState('USCITA');
  const [categoryId, setCategoryId] = useState('');
  const [accountId, setAccountId] = useState('');
  const [dayOfMonth, setDayOfMonth] = useState('1');
  const [autoConfirm, setAutoConfirm] = useState(false);
  const [recurrenceInterval, setRecurrenceInterval] = useState('MONTHLY');

  useEffect(() => {
    loadAllData();
  }, []);

  const loadAllData = async () => {
    try {
      const [recData, catData] = await Promise.all([
        fetchRecurringTransactions(),
        fetchCategories()
      ]);
      setRecurringList(recData);
      setCategories(catData);
      if (catData.length > 0 && !categoryId) {
        setCategoryId(catData[0].id);
      }
    } catch (err) {
      console.error('Failed to load recurring transactions', err);
    }
  };

  const categoryMap = useMemo(() => {
    return Object.fromEntries(categories.map(c => [c.id, c]));
  }, [categories]);

  const handleOpenCreate = () => {
    setEditingItem(null);
    setDescription('');
    setAmount('');
    setAccountId('');
    setType('USCITA');
    setCategoryId(categories.length > 0 ? categories[0].id : '');
    setDayOfMonth('1');
    setAutoConfirm(false);
    setRecurrenceInterval('MONTHLY');
    setShowModal(true);
  };

  const handleOpenEdit = (item) => {
    setEditingItem(item);
    setDescription(item.description);
    setAmount(item.amount);
    setType(item.type);
    setCategoryId(item.categoryId || (categories.length > 0 ? categories[0].id : ''));
    setAccountId(item.accountId || '');
    setDayOfMonth(item.dayOfMonth ? String(item.dayOfMonth) : '1');
    setAutoConfirm(Boolean(item.autoConfirm));
    setRecurrenceInterval(item.recurrenceInterval || 'MONTHLY');
    setShowModal(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!description || !amount) return;

    try {
      const payload = {
        description,
        amount: Number(amount),
        type,
        categoryId: categoryId || null,
        accountId: accountId || null,
        dayOfMonth: Number(dayOfMonth) || 1,
        autoConfirm,
        recurrenceInterval
      };

      if (editingItem) {
        await updateRecurringTransaction(editingItem.id, payload);
      } else {
        await createRecurringTransaction(payload);
      }

      setShowModal(false);
      loadAllData();
    } catch (err) {
      alert(`Errore nel salvataggio: ${err.message}`);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Eliminare questa spesa/entrata ricorrente?')) return;
    try {
      await deleteRecurringTransaction(id);
      setRecurringList(prev => prev.filter(r => r.id !== id));
    } catch (err) {
      alert(`Errore: ${err.message}`);
    }
  };

  const handleConfirm = async (item) => {
    try {
      await confirmRecurringTransaction(item.id);
      alert(`Transazione "${item.description}" confermata e registrata con successo!`);
      loadAllData();
    } catch (err) {
      alert(`Errore durante la conferma: ${err.message}`);
    }
  };

  const handleProcessAuto = async () => {
    try {
      const res = await processAutoRecurringTransactions();
      alert(`Verifica completata: ${res.length} transazioni automatiche generate.`);
      loadAllData();
    } catch (err) {
      alert(`Errore: ${err.message}`);
    }
  };

  // Calculations for summary stats
  const totalMonthlyExpenses = recurringList
    .filter(r => r.type === 'USCITA' && r.active)
    .reduce((s, r) => s + Number(r.amount), 0);

  const totalMonthlyIncomes = recurringList
    .filter(r => r.type === 'ENTRATA' && r.active)
    .reduce((s, r) => s + Number(r.amount), 0);

  // Calendar calculation
  const calendarDays = useMemo(() => {
    const year = selectedDate.getFullYear();
    const month = selectedDate.getMonth();
    const firstDayIndex = new Date(year, month, 1).getDay(); // 0 = Sun
    const adjustedFirstDay = firstDayIndex === 0 ? 6 : firstDayIndex - 1; // 0 = Mon
    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const days = [];
    // Previous month empty slots
    for (let i = 0; i < adjustedFirstDay; i++) {
      days.push({ day: null });
    }
    // Days in current month
    for (let d = 1; d <= daysInMonth; d++) {
      const itemsForDay = recurringList.filter(r => r.active && Number(r.dayOfMonth) === d);
      days.push({ day: d, items: itemsForDay });
    }
    return days;
  }, [selectedDate, recurringList]);

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', animation: 'fadeIn 0.4s ease-out' }}>
      <header className="dashboard-header" style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{
            width: '50px', height: '50px',
            background: 'var(--accent-gradient)',
            borderRadius: 'var(--radius-md)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: 'var(--shadow-glow)'
          }}>
            <CalendarClock size={24} color="white" />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Ricorrenti & Scadenze</h1>
            <p className="text-muted text-sm">Gestisci le spese fisse periodiche e visualizza le scadenze del mese.</p>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button
            onClick={handleProcessAuto}
            className="btn btn-glass"
            title="Esegue il controllo delle scadenze con conferma automatica"
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem' }}
          >
            <Zap size={16} color="var(--accent-secondary)" />
            <span>Processa Auto</span>
          </button>

          <button
            onClick={handleOpenCreate}
            className="btn btn-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
          >
            <Plus size={18} />
            <span>Nuova Ricorrenza</span>
          </button>
        </div>
      </header>

      {/* Overview Stat Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 240px), 1fr))', gap: '1rem', marginBottom: '2rem' }}>
        <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)' }}>
          <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Uscite Fisse Mensili</span>
          <h2 style={{ margin: '0.5rem 0', color: 'var(--danger)', fontSize: '2rem' }}>
            -€{totalMonthlyExpenses.toLocaleString('it-IT', { minimumFractionDigits: 2 })}
          </h2>
          <span className="text-muted text-sm">{recurringList.filter(r => r.type === 'USCITA').length} abbonamenti o spese fisse</span>
        </div>

        <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)' }}>
          <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Entrate Fisse Mensili</span>
          <h2 style={{ margin: '0.5rem 0', color: 'var(--success)', fontSize: '2rem' }}>
            +€{totalMonthlyIncomes.toLocaleString('it-IT', { minimumFractionDigits: 2 })}
          </h2>
          <span className="text-muted text-sm">Stipendi o entrate periodiche</span>
        </div>

        <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)' }}>
          <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Saldo Ricorrente Netto</span>
          <h2 style={{ margin: '0.5rem 0', color: totalMonthlyIncomes >= totalMonthlyExpenses ? 'var(--success)' : 'var(--danger)', fontSize: '2rem' }}>
            €{(totalMonthlyIncomes - totalMonthlyExpenses).toLocaleString('it-IT', { minimumFractionDigits: 2 })}
          </h2>
          <span className="text-muted text-sm">Impatto mensile a regime</span>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '2rem', background: 'rgba(255,255,255,0.03)', padding: '0.4rem', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border-glass)' }}>
        <button
          onClick={() => setActiveTab('calendar')}
          className={`btn ${activeTab === 'calendar' ? 'btn-primary' : 'btn-glass'}`}
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
        >
          <CalendarIcon size={18} /> Calendario Scadenze
        </button>
        <button
          onClick={() => setActiveTab('list')}
          className={`btn ${activeTab === 'list' ? 'btn-primary' : 'btn-glass'}`}
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
        >
          <List size={18} /> Elenco Ricorrenze ({recurringList.length})
        </button>
      </div>

      {/* TAB 1: CALENDAR VIEW */}
      {activeTab === 'calendar' && (
        <div className="glass-panel" style={{ padding: '2rem', borderRadius: 'var(--radius-lg)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
            <h3 style={{ margin: 0, textTransform: 'capitalize' }}>
              {selectedDate.toLocaleDateString('it-IT', { month: 'long', year: 'numeric' })}
            </h3>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                className="btn btn-glass"
                style={{ padding: '0.4rem 0.6rem' }}
                onClick={() => setSelectedDate(new Date(selectedDate.getFullYear(), selectedDate.getMonth() - 1, 1))}
              >
                <ChevronLeft size={16} />
              </button>
              <button
                className="btn btn-glass"
                style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem' }}
                onClick={() => setSelectedDate(new Date())}
              >
                Oggi
              </button>
              <button
                className="btn btn-glass"
                style={{ padding: '0.4rem 0.6rem' }}
                onClick={() => setSelectedDate(new Date(selectedDate.getFullYear(), selectedDate.getMonth() + 1, 1))}
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>

          {/* Weekday headers */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', textAlign: 'center', fontWeight: 600, color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
            {['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'].map(d => (
              <div key={d} style={{ padding: '0.5rem 0' }}>{d}</div>
            ))}
          </div>

          {/* Calendar grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '6px' }}>
            {calendarDays.map((cell, idx) => {
              if (!cell.day) {
                return <div key={`empty-${idx}`} style={{ minHeight: '80px', background: 'rgba(255,255,255,0.01)', borderRadius: '6px' }} />;
              }

              const isToday =
                new Date().getDate() === cell.day &&
                new Date().getMonth() === selectedDate.getMonth() &&
                new Date().getFullYear() === selectedDate.getFullYear();

              return (
                <div
                  key={`day-${cell.day}`}
                  style={{
                    minHeight: '85px',
                    padding: '6px',
                    borderRadius: '8px',
                    background: isToday ? 'rgba(197, 160, 40, 0.1)' : 'rgba(255,255,255,0.03)',
                    border: isToday ? '1px solid var(--accent-secondary)' : '1px solid rgba(255,255,255,0.04)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '4px'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{
                      fontSize: '0.8rem',
                      fontWeight: isToday ? 700 : 500,
                      color: isToday ? 'var(--accent-secondary)' : 'var(--text-muted)'
                    }}>
                      {cell.day}
                    </span>
                  </div>

                  {/* Badges for recurring transactions */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', overflowY: 'auto' }}>
                    {cell.items && cell.items.map(it => {
                      const isExpense = it.type === 'USCITA';
                      return (
                        <div
                          key={it.id}
                          title={`${it.description} - €${it.amount} (${isExpense ? 'Uscita' : 'Entrata'})`}
                          style={{
                            fontSize: '0.7rem',
                            padding: '2px 4px',
                            borderRadius: '4px',
                            background: isExpense ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.2)',
                            color: isExpense ? '#fca5a5' : '#86efac',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                          }}
                        >
                          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{it.description}</span>
                          <span style={{ fontWeight: 600, marginLeft: '4px' }}>€{Math.round(it.amount)}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 2: LIST VIEW */}
      {activeTab === 'list' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {recurringList.length === 0 ? (
            <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
              <CalendarClock size={48} color="var(--accent-primary)" style={{ opacity: 0.6, marginBottom: '1rem' }} />
              <h3>Nessuna transazione ricorrente configurata</h3>
              <p className="text-muted" style={{ maxWidth: '400px', margin: '0.5rem auto 1.5rem' }}>
                Aggiungi affitto, stipendio, palestra o abbonamenti per non dimenticare le scadenze.
              </p>
              <button onClick={handleOpenCreate} className="btn btn-primary">
                Crea prima ricorrenza
              </button>
            </div>
          ) : (
            recurringList.map((item) => {
              const isExpense = item.type === 'USCITA';
              const cat = categoryMap[item.categoryId];
              const due = item.nextDueDate ? new Date(item.nextDueDate).toLocaleDateString('it-IT') : `Giorno ${item.dayOfMonth} del mese`;

              return (
                <div
                  key={item.id}
                  className="glass-panel"
                  style={{
                    padding: '1.25rem 1.5rem',
                    borderRadius: 'var(--radius-md)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: '1rem'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', minWidth: '240px' }}>
                    <div style={{
                      width: '44px', height: '44px',
                      borderRadius: '12px',
                      background: isExpense ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center'
                    }}>
                      {isExpense ? <ArrowDownLeft size={20} color="var(--danger)" /> : <ArrowUpRight size={20} color="var(--success)" />}
                    </div>

                    <div>
                      <h4 style={{ margin: 0, fontSize: '1.1rem' }}>{item.description}</h4>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.25rem' }}>
                        <span style={{
                          fontSize: '0.75rem',
                          padding: '2px 8px',
                          borderRadius: '10px',
                          background: cat ? `${cat.color}25` : 'rgba(255,255,255,0.06)',
                          color: cat ? cat.color : 'var(--text-muted)'
                        }}>
                          {cat ? cat.name : 'Senza Categoria'}
                        </span>
                        <span className="text-muted text-xs">
                          Ogni {item.dayOfMonth}° del mese • Prossima: {due}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{
                        fontSize: '1.3rem',
                        fontWeight: 700,
                        color: isExpense ? 'var(--danger)' : 'var(--success)'
                      }}>
                        {isExpense ? '-' : '+'}€{Number(item.amount).toLocaleString('it-IT', { minimumFractionDigits: 2 })}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: item.autoConfirm ? '#34d399' : 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '0.25rem', justifyContent: 'flex-end' }}>
                        {item.autoConfirm ? <Zap size={12} /> : <Clock size={12} />}
                        <span>{item.autoConfirm ? 'Auto-conferma ON' : 'Richiede conferma'}</span>
                      </div>
                    </div>

                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        onClick={() => handleConfirm(item)}
                        className="btn"
                        title="Conferma e registra ora come transazione"
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '0.35rem',
                          padding: '0.45rem 0.8rem',
                          fontSize: '0.8rem',
                          background: 'rgba(16, 185, 129, 0.15)',
                          border: '1px solid rgba(16, 185, 129, 0.3)',
                          color: '#34d399',
                          borderRadius: 'var(--radius-md)'
                        }}
                      >
                        <Check size={14} /> Conferma
                      </button>

                      <button
                        onClick={() => handleOpenEdit(item)}
                        className="btn-icon"
                        title="Modifica"
                        style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '4px' }}
                      >
                        <Pencil size={16} />
                      </button>

                      <button
                        onClick={() => handleDelete(item.id)}
                        className="btn-icon"
                        title="Elimina"
                        style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '4px' }}
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {/* Modal Crea / Modifica */}
      {showModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(5px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000, padding: '1rem'
        }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '480px', padding: '2rem', borderRadius: 'var(--radius-lg)' }}>
            <h2 style={{ marginBottom: '1.5rem' }}>
              {editingItem ? 'Modifica Ricorrenza' : 'Nuova Transazione Ricorrente'}
            </h2>

            <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="form-group">
                <label>Descrizione *</label>
                <input
                  type="text"
                  required
                  placeholder="Es. Netflix, Affitto, Stipendio..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label>Importo (€) *</label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    placeholder="0.00"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label>Tipo</label>
                  <select
                    value={type}
                    onChange={(e) => setType(e.target.value)}
                    className="input-month"
                    style={{ width: '100%', height: '42px' }}
                  >
                    <option value="USCITA">Uscita (Spesa)</option>
                    <option value="ENTRATA">Entrata</option>
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label>Categoria</label>
                  <select
                    value={categoryId}
                    onChange={(e) => setCategoryId(e.target.value)}
                    className="input-month"
                    style={{ width: '100%', height: '42px' }}
                  >
                    <option value="">Seleziona categoria</option>
                    {categories
                      .filter(c => c.expense === (type === 'USCITA'))
                      .map(c => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                      ))}
                  </select>
                </div>

                <div className="form-group">
                  <AccountSelect value={accountId} onChange={setAccountId} className="input-month" />
                </div>

                <div className="form-group">
                  <label>Giorno del Mese (1-31)</label>
                  <input
                    type="number"
                    min="1"
                    max="31"
                    required
                    value={dayOfMonth}
                    onChange={(e) => setDayOfMonth(e.target.value)}
                  />
                </div>
              </div>

              <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginTop: '0.5rem' }}>
                <input
                  type="checkbox"
                  id="autoConfirmCheck"
                  checked={autoConfirm}
                  onChange={(e) => setAutoConfirm(e.target.checked)}
                  style={{ width: '18px', height: '18px', cursor: 'pointer', accentColor: 'var(--accent-primary)' }}
                />
                <label htmlFor="autoConfirmCheck" style={{ margin: 0, cursor: 'pointer', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                  Auto-conferma (genera automaticamente la transazione alla data di scadenza)
                </label>
              </div>

              <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="btn btn-glass"
                  style={{ flex: 1 }}
                >
                  Annulla
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{ flex: 1 }}
                >
                  Salva Ricorrenza
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default RecurringTransactions;
