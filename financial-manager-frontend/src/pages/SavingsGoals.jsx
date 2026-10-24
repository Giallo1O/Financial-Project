import React, { useState, useEffect } from 'react';
import {
  Target, Plus, CheckCircle2, TrendingUp, Calendar,
  MoreVertical, Pencil, Trash2, ArrowUpRight, ArrowDownLeft,
  Sparkles, Award
} from 'lucide-react';
import {
  fetchSavingsGoals, createSavingsGoal, updateSavingsGoal,
  deleteSavingsGoal, depositToSavingsGoal, withdrawFromSavingsGoal
} from '../api';
import '../components/Components.css';
import { eur } from '../utils/format';

const PRESET_COLORS = [
  '#9e5545', // terracotta
  '#c5a028', // gold
  '#10b981', // emerald
  '#6366f1', // indigo
  '#06b6d4', // cyan
  '#ec4899', // pink
  '#8b5cf6'  // purple
];

const SavingsGoals = () => {
  const [goals, setGoals] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modal State for Create/Edit
  const [showFormModal, setShowFormModal] = useState(false);
  const [editingGoal, setEditingGoal] = useState(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [targetAmount, setTargetAmount] = useState('');
  const [currentAmount, setCurrentAmount] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [color, setColor] = useState(PRESET_COLORS[0]);

  // Modal State for Deposit / Withdraw
  const [actionModal, setActionModal] = useState({ open: false, type: 'deposit', goal: null });
  const [actionAmount, setActionAmount] = useState('');

  useEffect(() => {
    loadGoals();
  }, []);

  const loadGoals = async () => {
    try {
      setLoading(true);
      const data = await fetchSavingsGoals();
      setGoals(data);
    } catch (err) {
      console.error('Failed to load savings goals', err);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenCreate = () => {
    setEditingGoal(null);
    setName('');
    setDescription('');
    setTargetAmount('');
    setCurrentAmount('0');
    setTargetDate('');
    setColor(PRESET_COLORS[0]);
    setShowFormModal(true);
  };

  const handleOpenEdit = (goal) => {
    setEditingGoal(goal);
    setName(goal.name);
    setDescription(goal.description || '');
    setTargetAmount(goal.targetAmount);
    setCurrentAmount(goal.currentAmount);
    setTargetDate(goal.targetDate || '');
    setColor(goal.color || PRESET_COLORS[0]);
    setShowFormModal(true);
  };

  const handleSaveGoal = async (e) => {
    e.preventDefault();
    if (!name || !targetAmount) return;

    try {
      const payload = {
        name,
        description,
        targetAmount: Number(targetAmount),
        currentAmount: Number(currentAmount) || 0,
        targetDate: targetDate || null,
        color
      };

      if (editingGoal) {
        await updateSavingsGoal(editingGoal.id, payload);
      } else {
        await createSavingsGoal(payload);
      }

      setShowFormModal(false);
      loadGoals();
    } catch (err) {
      alert(`Errore nel salvataggio: ${err.message}`);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Eliminare questo obiettivo di risparmio?')) return;
    try {
      await deleteSavingsGoal(id);
      setGoals(prev => prev.filter(g => g.id !== id));
    } catch (err) {
      alert(`Errore nell'eliminazione: ${err.message}`);
    }
  };

  const handleOpenAction = (goal, type) => {
    setActionModal({ open: true, type, goal });
    setActionAmount('');
  };

  const handleExecuteAction = async (e) => {
    e.preventDefault();
    const amt = Number(actionAmount);
    if (!amt || amt <= 0) return;

    try {
      if (actionModal.type === 'deposit') {
        await depositToSavingsGoal(actionModal.goal.id, amt);
      } else {
        await withdrawFromSavingsGoal(actionModal.goal.id, amt);
      }
      setActionModal({ open: false, type: 'deposit', goal: null });
      loadGoals();
    } catch (err) {
      alert(`Errore nell'operazione: ${err.message}`);
    }
  };

  // Aggregated stats
  const totalSaved = goals.reduce((s, g) => s + Number(g.currentAmount || 0), 0);
  const totalTarget = goals.reduce((s, g) => s + Number(g.targetAmount || 0), 0);
  const overallPct = totalTarget > 0 ? Math.min(100, Math.round((totalSaved / totalTarget) * 100)) : 0;
  const completedCount = goals.filter(g => g.completed).length;

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
            <Target size={24} color="white" />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.8rem' }}>Obiettivi di Risparmio</h1>
            <p className="text-muted text-sm">Crea i tuoi salvadanai virtuali e monitora i traguardi.</p>
          </div>
        </div>

        <button onClick={handleOpenCreate} className="btn btn-primary" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Plus size={18} />
          <span>Nuovo Salvadanaio</span>
        </button>
      </header>

      {/* Overview Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 240px), 1fr))', gap: '1rem', marginBottom: '2rem' }}>
        <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)' }}>
          <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Totale Risparmiato</span>
          <h2 style={{ margin: '0.5rem 0', color: 'var(--success)', fontSize: '2rem' }}>
            {eur(totalSaved)}
          </h2>
          <span className="text-muted text-sm">su {eur(totalTarget)} prefissati</span>
        </div>

        <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)' }}>
          <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Avanzamento Complessivo</span>
          <h2 style={{ margin: '0.5rem 0', color: 'var(--accent-secondary)', fontSize: '2rem' }}>
            {overallPct}%
          </h2>
          <div style={{ width: '100%', height: '8px', background: 'rgba(255,255,255,0.08)', borderRadius: '4px', overflow: 'hidden' }}>
            <div style={{ width: `${overallPct}%`, height: '100%', background: 'var(--accent-gradient)', transition: 'width 0.4s ease' }} />
          </div>
        </div>

        <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)' }}>
          <span className="text-muted text-xs uppercase" style={{ letterSpacing: '0.05em' }}>Traguardi Raggiunti</span>
          <h2 style={{ margin: '0.5rem 0', color: 'var(--text-primary)', fontSize: '2rem' }}>
            {completedCount} <span style={{ fontSize: '1.2rem', color: 'var(--text-muted)' }}>/ {goals.length}</span>
          </h2>
          <span className="text-muted text-sm">{goals.length - completedCount} obiettivi ancora in corso</span>
        </div>
      </div>

      {/* Goals Grid */}
      {loading ? (
        <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>Caricamento salvadanai...</div>
      ) : goals.length === 0 ? (
        <div className="glass-panel" style={{ padding: '4rem', textAlign: 'center' }}>
          <Target size={48} color="var(--accent-primary)" style={{ opacity: 0.6, marginBottom: '1rem' }} />
          <h3>Nessun salvadanaio creato</h3>
          <p className="text-muted" style={{ maxWidth: '400px', margin: '0.5rem auto 1.5rem' }}>
            Imposta un obiettivo come un fondo di emergenza, una vacanza o un acquisto importante per tracciare i tuoi progressi.
          </p>
          <button onClick={handleOpenCreate} className="btn btn-primary">
            Crea il tuo primo obiettivo
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 340px), 1fr))', gap: '1.5rem' }}>
          {goals.map((goal) => {
            const current = Number(goal.currentAmount || 0);
            const target = Number(goal.targetAmount || 0);
            const pct = target > 0 ? Math.min(100, Math.round((current / target) * 100)) : 0;
            const remaining = Math.max(0, target - current);
            const goalColor = goal.color || 'var(--accent-primary)';

            // Smart Monthly Recommendation
            let monthlyAdvice = null;
            if (goal.targetDate && remaining > 0) {
              const targetD = new Date(goal.targetDate);
              const now = new Date();
              const diffMonths = (targetD.getFullYear() - now.getFullYear()) * 12 + (targetD.getMonth() - now.getMonth());
              if (diffMonths > 0) {
                const perMonth = remaining / diffMonths;
                monthlyAdvice = `~€${Math.round(perMonth)}/mese nei prossimi ${diffMonths} mesi`;
              } else {
                monthlyAdvice = 'Scadenza questo mese';
              }
            }

            return (
              <div
                key={goal.id}
                className="glass-panel"
                style={{
                  padding: '1.5rem',
                  borderRadius: 'var(--radius-lg)',
                  border: `1px solid ${goalColor}40`,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '1rem',
                  position: 'relative',
                  overflow: 'hidden'
                }}
              >
                {/* Top color bar */}
                <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '4px', background: goalColor }} />

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <div style={{
                      width: '40px', height: '40px',
                      borderRadius: '12px',
                      background: `${goalColor}25`,
                      border: `1px solid ${goalColor}50`,
                      display: 'flex', alignItems: 'center', justifyContent: 'center'
                    }}>
                      <Target size={20} color={goalColor} />
                    </div>
                    <div>
                      <h3 style={{ margin: 0, fontSize: '1.2rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        {goal.name}
                        {goal.completed && <Award size={18} color="var(--accent-secondary)" />}
                      </h3>
                      {goal.description && <p className="text-muted text-xs" style={{ margin: 0 }}>{goal.description}</p>}
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '0.25rem' }}>
                    <button
                      onClick={() => handleOpenEdit(goal)}
                      className="btn-icon"
                      title="Modifica"
                      style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '4px' }}
                    >
                      <Pencil size={16} />
                    </button>
                    <button
                      onClick={() => handleDelete(goal.id)}
                      className="btn-icon"
                      title="Elimina"
                      style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '4px' }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>

                {/* Amount progress */}
                <div style={{ marginTop: '0.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '0.35rem' }}>
                    <span style={{ fontSize: '1.5rem', fontWeight: 700, color: goal.completed ? 'var(--success)' : 'var(--text-primary)' }}>
                      {eur(current)}
                    </span>
                    <span className="text-muted text-sm">
                      Target: {eur(target)}
                    </span>
                  </div>

                  {/* Progress bar */}
                  <div style={{ width: '100%', height: '10px', background: 'rgba(255,255,255,0.06)', borderRadius: '6px', overflow: 'hidden' }}>
                    <div style={{
                      width: `${pct}%`,
                      height: '100%',
                      background: goal.completed ? 'var(--success)' : goalColor,
                      borderRadius: '6px',
                      transition: 'width 0.4s ease'
                    }} />
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginTop: '0.4rem', color: 'var(--text-muted)' }}>
                    <span>{pct}% raggiunto</span>
                    <span>{goal.completed ? 'Completato! 🎉' : `Mancano: ${eur(remaining)}`}</span>
                  </div>
                </div>

                {/* Target Date & Advice */}
                <div style={{ fontSize: '0.8rem', background: 'rgba(255,255,255,0.02)', padding: '0.5rem 0.75rem', borderRadius: '8px', border: '1px solid var(--border-glass)' }}>
                  {goal.targetDate ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: 'var(--text-secondary)' }}>
                      <Calendar size={14} />
                      <span>Scadenza: {new Date(goal.targetDate).toLocaleDateString('it-IT')}</span>
                      {monthlyAdvice && <span style={{ marginLeft: 'auto', color: 'var(--accent-secondary)', fontWeight: 500 }}>{monthlyAdvice}</span>}
                    </div>
                  ) : (
                    <span className="text-muted">Nessuna data di scadenza impostata</span>
                  )}
                </div>

                {/* Action Buttons */}
                <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
                  <button
                    onClick={() => handleOpenAction(goal, 'deposit')}
                    className="btn"
                    style={{
                      flex: 1,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '0.4rem',
                      fontSize: '0.85rem',
                      padding: '0.5rem',
                      background: 'rgba(16, 185, 129, 0.15)',
                      border: '1px solid rgba(16, 185, 129, 0.3)',
                      color: '#34d399',
                      borderRadius: 'var(--radius-md)'
                    }}
                  >
                    <ArrowUpRight size={16} /> Versa
                  </button>

                  <button
                    onClick={() => handleOpenAction(goal, 'withdraw')}
                    className="btn"
                    style={{
                      flex: 1,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '0.4rem',
                      fontSize: '0.85rem',
                      padding: '0.5rem',
                      background: 'rgba(239, 68, 68, 0.15)',
                      border: '1px solid rgba(239, 68, 68, 0.3)',
                      color: '#f87171',
                      borderRadius: 'var(--radius-md)'
                    }}
                  >
                    <ArrowDownLeft size={16} /> Preleva
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Crea / Modifica */}
      {showFormModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(5px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000, padding: '1rem'
        }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '480px', padding: '2rem', borderRadius: 'var(--radius-lg)' }}>
            <h2 style={{ marginBottom: '1.5rem' }}>
              {editingGoal ? 'Modifica Salvadanaio' : 'Nuovo Salvadanaio'}
            </h2>

            <form onSubmit={handleSaveGoal} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="form-group">
                <label>Nome Obiettivo *</label>
                <input
                  type="text"
                  required
                  placeholder="Es. Fondo Emergenza, Vacanze..."
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label>Descrizione (opzionale)</label>
                <input
                  type="text"
                  placeholder="Note o motivazione"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label>Target da Raggiungere (€) *</label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    placeholder="5000"
                    value={targetAmount}
                    onChange={(e) => setTargetAmount(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label>Importo Iniziale (€)</label>
                  <input
                    type="number"
                    step="0.01"
                    placeholder="0"
                    value={currentAmount}
                    onChange={(e) => setCurrentAmount(e.target.value)}
                  />
                </div>
              </div>

              <div className="form-group">
                <label>Data di Scadenza / Traguardo (opzionale)</label>
                <input
                  type="date"
                  value={targetDate}
                  onChange={(e) => setTargetDate(e.target.value)}
                  className="input-month"
                />
              </div>

              <div className="form-group">
                <label>Colore Salvadanaio</label>
                <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.25rem' }}>
                  {PRESET_COLORS.map(c => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      style={{
                        width: '28px', height: '28px',
                        borderRadius: '50%',
                        background: c,
                        border: color === c ? '2px solid white' : 'none',
                        cursor: 'pointer',
                        boxShadow: color === c ? `0 0 8px ${c}` : 'none'
                      }}
                    />
                  ))}
                </div>
              </div>

              <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setShowFormModal(false)}
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
                  Salva Obiettivo
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Versamento / Prelievo */}
      {actionModal.open && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(5px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000, padding: '1rem'
        }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '400px', padding: '2rem', borderRadius: 'var(--radius-lg)' }}>
            <h3 style={{ marginBottom: '0.5rem' }}>
              {actionModal.type === 'deposit' ? 'Versa nel Salvadanaio' : 'Preleva dal Salvadanaio'}
            </h3>
            <p className="text-muted text-sm" style={{ marginBottom: '1.5rem' }}>
              Obiettivo: <strong>{actionModal.goal?.name}</strong> (Attuale: {eur(Number(actionModal.goal?.currentAmount || 0))})
            </p>

            <form onSubmit={handleExecuteAction} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="form-group">
                <label>Importo (€)</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  autoFocus
                  placeholder="0.00"
                  value={actionAmount}
                  onChange={(e) => setActionAmount(e.target.value)}
                />
              </div>

              {/* Quick Amount Buttons */}
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                {[20, 50, 100, 200].map(amt => (
                  <button
                    key={amt}
                    type="button"
                    className="btn btn-glass"
                    style={{ flex: 1, padding: '0.35rem', fontSize: '0.8rem' }}
                    onClick={() => setActionAmount(String(amt))}
                  >
                    +€{amt}
                  </button>
                ))}
              </div>

              <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setActionModal({ open: false, type: 'deposit', goal: null })}
                  className="btn btn-glass"
                  style={{ flex: 1 }}
                >
                  Annulla
                </button>
                <button
                  type="submit"
                  className={`btn ${actionModal.type === 'deposit' ? 'btn-success' : 'btn-danger'}`}
                  style={{ flex: 1 }}
                >
                  {actionModal.type === 'deposit' ? 'Conferma Versamento' : 'Conferma Prelievo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default SavingsGoals;
