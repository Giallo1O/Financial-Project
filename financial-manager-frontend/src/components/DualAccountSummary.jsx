import React from 'react';
import { ArrowUpRight, ArrowDownRight, Wallet, Users, User, Info } from 'lucide-react';
import './Components.css';
import { eur } from '../utils/format';

const DualAccountSummary = ({ summary }) => {
  if (!summary) return null;

  const {
    generalIncome = 0,
    generalExpenses = 0,
    generalBalance = 0,
    personalIncome = 0,
    personalExpenses = 0,
    personalBalance = 0,
    rossellaTotalContribution = 0,
    andreaReimbursements = 0
  } = summary;

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 460px), 1fr))',
      gap: '1.5rem'
    }}>
      {/* ─── CARD 1: TOTALE GENERALE (Casa & Spazi) ────────────────────────── */}
      <div className="glass-panel summary-card" style={{ padding: '1.5rem', borderRadius: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.2rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.2rem' }}>
              <div style={{
                width: '32px', height: '32px', borderRadius: '8px',
                background: 'rgba(236, 72, 153, 0.15)', color: '#f472b6',
                display: 'flex', alignItems: 'center', justifyContent: 'center'
              }}>
                <Users size={18} />
              </div>
              <h3 style={{ fontSize: '1.15rem', margin: 0 }}>Totale Generale del Mese</h3>
            </div>
            <p className="text-muted text-xs">
              Tutto il bilancio complessivo (stipendio tuo + contributi di Rossella + tutte le spese di casa).
            </p>
          </div>

          <div style={{ textAlign: 'right' }}>
            <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Saldo Generale
            </span>
            <h2 className={generalBalance >= 0 ? 'text-success' : 'text-danger'} style={{ fontSize: '1.8rem', margin: '0.1rem 0 0' }}>
              {eur(generalBalance)}
            </h2>
          </div>
        </div>

        <div className="summary-stats" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="stat-box income-box" style={{ padding: '0.8rem 1rem' }}>
            <div className="stat-icon bg-success-muted">
              <ArrowUpRight size={18} className="text-success" />
            </div>
            <div>
              <p className="text-muted text-xs">Entrate Generali</p>
              <h4 style={{ margin: '0.1rem 0 0' }}>{eur(generalIncome)}</h4>
              {rossellaTotalContribution > 0 && (
                <span className="text-muted text-xs" style={{ color: '#f472b6' }}>
                  (Rossella: {eur(rossellaTotalContribution)})
                </span>
              )}
            </div>
          </div>

          <div className="stat-box expense-box" style={{ padding: '0.8rem 1rem' }}>
            <div className="stat-icon bg-danger-muted">
              <ArrowDownRight size={18} className="text-danger" />
            </div>
            <div>
              <p className="text-muted text-xs">Uscite Generali</p>
              <h4 style={{ margin: '0.1rem 0 0' }}>{eur(generalExpenses)}</h4>
              <span className="text-muted text-xs">Tutte le spese di casa</span>
            </div>
          </div>
        </div>
      </div>

      {/* ─── CARD 2: SOLO PERSONALE ANDREA ─────────────────────────────────── */}
      <div className="glass-panel summary-card" style={{ padding: '1.5rem', borderRadius: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.2rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.2rem' }}>
              <div style={{
                width: '32px', height: '32px', borderRadius: '8px',
                background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8',
                display: 'flex', alignItems: 'center', justifyContent: 'center'
              }}>
                <User size={18} />
              </div>
              <h3 style={{ fontSize: '1.15rem', margin: 0 }}>Solo Personale Tuo</h3>
            </div>
            <p className="text-muted text-xs">
              Le tue sole entrate (stipendio) e le tue sole spese personali (non rimborsate dagli spazi).
            </p>
          </div>

          <div style={{ textAlign: 'right' }}>
            <span className="text-muted text-xs" style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Tuo Risparmio Netto
            </span>
            <h2 className={personalBalance >= 0 ? 'text-success' : 'text-danger'} style={{ fontSize: '1.8rem', margin: '0.1rem 0 0' }}>
              {eur(personalBalance)}
            </h2>
          </div>
        </div>

        <div className="summary-stats" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="stat-box income-box" style={{ padding: '0.8rem 1rem' }}>
            <div className="stat-icon bg-success-muted">
              <ArrowUpRight size={18} className="text-success" />
            </div>
            <div>
              <p className="text-muted text-xs">Entrate Tue</p>
              <h4 style={{ margin: '0.1rem 0 0' }}>{eur(personalIncome)}</h4>
              <span className="text-muted text-xs">Tuo stipendio</span>
            </div>
          </div>

          <div className="stat-box expense-box" style={{ padding: '0.8rem 1rem' }}>
            <div className="stat-icon bg-danger-muted">
              <ArrowDownRight size={18} className="text-danger" />
            </div>
            <div>
              <p className="text-muted text-xs">Uscite Tue Personali</p>
              <h4 style={{ margin: '0.1rem 0 0' }}>{eur(personalExpenses)}</h4>
              {andreaReimbursements > 0 && (
                <span className="text-muted text-xs" style={{ color: '#818cf8' }}>
                  (-{eur(andreaReimbursements)} dagli spazi)
                </span>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DualAccountSummary;
