import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, AlertCircle, Info, TrendingUp } from 'lucide-react';
import { fetchForecast, fetchAlerts } from '../api';
import PrivacyMask from './PrivacyMask';
import './Insights.css';

const eur = (n) => Number(n || 0).toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
const ICONS = { danger: AlertTriangle, warning: AlertCircle, info: Info };

/** Avvisi (budget, scadenze, debiti) e previsione di fine mese; non mostra nulla se il server non risponde. */
const Insights = () => {
  const [forecast, setForecast] = useState(null);
  const [alerts, setAlerts] = useState([]);

  useEffect(() => {
    fetchForecast().then(setForecast).catch(() => {});
    fetchAlerts().then(setAlerts).catch(() => {});
  }, []);

  if (!forecast && alerts.length === 0) return null;
  const balance = forecast ? Number(forecast.projectedBalance) : 0;

  return (
    <section className="insights">
      {alerts.length > 0 && (
        <div className="glass-panel insights-alerts" data-testid="alerts">
          {alerts.map(a => {
            const Icon = ICONS[a.severity] || Info;
            return (
              <Link key={`${a.type}-${a.message}`} to={a.link} className={`insights-alert ${a.severity}`}>
                <Icon size={18} /> <span>{a.message}</span>
              </Link>
            );
          })}
        </div>
      )}

      {forecast && (
        <div className="glass-panel insights-forecast" data-testid="forecast">
          <div className="insights-head">
            <TrendingUp size={18} />
            <strong>Previsione a fine mese</strong>
            <span className="text-muted text-sm">{forecast.daysLeft} giorni al termine</span>
          </div>
          <div className={`insights-balance ${balance < 0 ? 'neg' : 'pos'}`}>
            <PrivacyMask>{balance >= 0 ? '+' : ''}{eur(balance)}</PrivacyMask>
          </div>
          <p className="text-muted text-sm">
            Entrate <PrivacyMask>{eur(forecast.projectedIncome)}</PrivacyMask> − uscite previste <PrivacyMask>{eur(forecast.projectedExpenses)}</PrivacyMask>
            {Number(forecast.pendingExpenses) > 0 && <> (di cui <PrivacyMask>{eur(forecast.pendingExpenses)}</PrivacyMask> di ricorrenze in arrivo)</>}.
            {' '}{forecast.reliable
              ? <>Ritmo di spesa variabile: <PrivacyMask>{eur(forecast.dailyVariable)}</PrivacyMask> al giorno.</>
              : 'Nei primi giorni del mese la stima delle spese variabili non è ancora affidabile.'}
          </p>
        </div>
      )}
    </section>
  );
};

export default Insights;
