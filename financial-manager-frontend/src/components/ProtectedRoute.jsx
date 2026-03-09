import React, { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { isAuthenticated } from '../auth';
import Layout from './Layout';

const ProtectedRoute = () => {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }
  return <Layout />;
};

export default ProtectedRoute;
