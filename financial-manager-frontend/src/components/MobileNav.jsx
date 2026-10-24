import React, { useState, useEffect } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, ArrowLeftRight, Camera, PiggyBank, Menu, X, BarChart3, CalendarClock,
  Target, TrendingUp, Tag, Upload, Eye, EyeOff, LogOut, ListChecks, Landmark, Users
} from 'lucide-react';
import { removeAuth } from '../auth';
import { usePrivacy } from '../context/PrivacyContext';

// Barra di navigazione per telefono (sotto i 900px di larghezza; sopra resta la sidebar).
const MAIN = [
  { to: '/dashboard', label: 'Home', icon: LayoutDashboard },
  { to: '/transactions', label: 'Movimenti', icon: ArrowLeftRight },
  { to: '/groceries', label: 'Scontrino', icon: Camera, primary: true },
  { to: '/shopping', label: 'Lista', icon: ListChecks },
];

const MORE = [
  { to: '/accounts', label: 'Conti', icon: Landmark },
  { to: '/duo', label: 'Spese in due', icon: Users },
  { to: '/budgets', label: 'Budget', icon: PiggyBank },
  { to: '/analytics', label: 'Analisi', icon: BarChart3 },
  { to: '/recurring', label: 'Ricorrenti', icon: CalendarClock },
  { to: '/goals', label: 'Salvadanaio', icon: Target },
  { to: '/comparisons', label: 'Confronti', icon: TrendingUp },
  { to: '/categories', label: 'Categorie', icon: Tag },
  { to: '/import', label: 'Importa CSV', icon: Upload },
];

const MobileNav = () => {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { privacyMode, togglePrivacyMode } = usePrivacy();

  const moreActive = MORE.some(m => pathname.startsWith(m.to));

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  const logout = () => {
    removeAuth();
    navigate('/login');
  };

  return (
    <>
      <nav className="mobile-nav" aria-label="Navigazione principale">
        {MAIN.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink key={item.to} to={item.to} onClick={() => setOpen(false)}
              className={({ isActive }) => `mobile-nav-item ${item.primary ? 'primary' : ''} ${isActive ? 'active' : ''}`}>
              <span className="mobile-nav-icon"><Icon size={item.primary ? 26 : 22} /></span>
              <span className="mobile-nav-label">{item.label}</span>
            </NavLink>
          );
        })}
        <button type="button" className={`mobile-nav-item ${moreActive || open ? 'active' : ''}`}
          aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(o => !o)}>
          <span className="mobile-nav-icon"><Menu size={22} /></span>
          <span className="mobile-nav-label">Altro</span>
        </button>
      </nav>

      {open && (
        <>
          <div className="mobile-backdrop" onClick={() => setOpen(false)} />
          <div className="mobile-sheet" role="dialog" aria-modal="true" aria-label="Altre sezioni">
            <div className="mobile-sheet-header">
              <strong>Altro</strong>
              <button type="button" aria-label="Chiudi" onClick={() => setOpen(false)} className="mobile-sheet-close">
                <X size={22} />
              </button>
            </div>
            <div className="mobile-sheet-grid">
              {MORE.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink key={item.to} to={item.to} onClick={() => setOpen(false)}
                    className={({ isActive }) => `mobile-sheet-tile ${isActive ? 'active' : ''}`}>
                    <Icon size={24} />
                    <span>{item.label}</span>
                  </NavLink>
                );
              })}
            </div>
            <div className="mobile-sheet-actions">
              <button type="button" onClick={togglePrivacyMode} className={`mobile-sheet-btn ${privacyMode ? 'active' : ''}`}>
                {privacyMode ? <EyeOff size={20} /> : <Eye size={20} />}
                <span>{privacyMode ? 'Privacy: attiva' : 'Privacy: spenta'}</span>
              </button>
              <button type="button" onClick={logout} className="mobile-sheet-btn">
                <LogOut size={20} /> <span>Esci</span>
              </button>
            </div>
          </div>
        </>
      )}
    </>
  );
};

export default MobileNav;
