import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { LayoutDashboard, ArrowLeftRight, BarChart3, LogOut, Wallet, Tag, Upload, PiggyBank } from 'lucide-react';
import { removeAuth, getUserInfo } from '../auth';
import './Sidebar.css';

const Sidebar = () => {
  const navigate = useNavigate();
  const userInfo = getUserInfo();

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

        <NavLink to="/comparisons" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
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
        <button onClick={handleLogout} className="logout-btn full-width">
          <LogOut size={20} />
          <span>Logout</span>
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;
