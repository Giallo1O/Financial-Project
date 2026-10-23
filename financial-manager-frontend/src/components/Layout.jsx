import React from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import MobileNav from './MobileNav';

const Layout = () => {
  return (
    <div className="app-layout">
      <Sidebar />
      <MobileNav />
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;
