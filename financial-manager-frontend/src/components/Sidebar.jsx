import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, ArrowLeftRight, BarChart3, LogOut, Wallet,
  Tag, Upload, PiggyBank, TrendingUp, Eye, EyeOff, Target, CalendarClock, ShoppingCart, ListChecks, Landmark, Users
} from 'lucide-react';
import { removeAuth } from '../auth';
import { usePrivacy } from '../context/PrivacyContext';
import './Sidebar.css';

const Sidebar = () => {
  const navigate = useNavigate();
  const { privacyMode, togglePrivacyMode } = usePrivacy();

  const handleLogout = () => {
    removeAuth();
    navigate('/login');
  };



  return (
    <aside className="sidebar glass-panel">
      <div className="sidebar-header">
        <div className="logo-container">
          <Wallet size={28} className="logo-icon" />
        </div>
        <h2 className="brand-title">FinManager</h2>
      </div>

      <nav className="sidebar-nav">
        <NavLink to="/dashboard" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <LayoutDashboard size={20} />
          <span>Dashboard</span>
        </NavLink>

        <NavLink to="/transactions" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <ArrowLeftRight size={20} />
          <span>Transactions</span>
        </NavLink>

        <NavLink to="/analytics" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <BarChart3 size={20} />
          <span>Analytics</span>
        </NavLink>

        <NavLink to="/budgets" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <PiggyBank size={20} />
          <span>Budgets</span>
        </NavLink>

        <NavLink to="/recurring" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <CalendarClock size={20} />
          <span>Ricorrenti</span>
        </NavLink>

        <NavLink to="/goals" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Target size={20} />
          <span>Salvadanaio</span>
        </NavLink>

        <NavLink to="/groceries" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <ShoppingCart size={20} />
          <span>Spesa</span>
        </NavLink>

        <NavLink to="/shopping" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <ListChecks size={20} />
          <span>Lista spesa</span>
        </NavLink>

        <NavLink to="/accounts" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Landmark size={20} />
          <span>Conti</span>
        </NavLink>

        <NavLink to="/duo" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Users size={20} />
          <span>Spese in due</span>
        </NavLink>

        <NavLink to="/comparisons" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <TrendingUp size={20} />
          <span>Comparisons</span>
        </NavLink>

        <NavLink to="/categories" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Tag size={20} />
          <span>Categories</span>
        </NavLink>

        <NavLink to="/import" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Upload size={20} />
          <span>Import CSV</span>
        </NavLink>
      </nav>


      <div className="sidebar-footer">
        <button
          onClick={togglePrivacyMode}
          className={`privacy-toggle-btn full-width ${privacyMode ? 'active' : ''}`}
          title={privacyMode ? 'Disattiva Privacy Mode (mostra cifre)' : 'Attiva Privacy Mode (nascondi cifre)'}
        >
          {privacyMode ? <EyeOff size={18} /> : <Eye size={18} />}
          <span>{privacyMode ? 'Privacy: ON' : 'Privacy: OFF'}</span>
        </button>

        <button onClick={handleLogout} className="logout-btn full-width">
          <LogOut size={20} />
          <span>Logout</span>
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;

