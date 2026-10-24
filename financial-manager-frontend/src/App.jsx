import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Transactions from './pages/Transactions';
import Analytics from './pages/Analytics';
import Budgets from './pages/Budgets';
import Categories from './pages/Categories';
import Comparisons from './pages/Comparisons';
import ImportTransactions from './pages/ImportTransactions';
import SavingsGoals from './pages/SavingsGoals';
import RecurringTransactions from './pages/RecurringTransactions';
import Groceries from './pages/Groceries';
import Shopping from './pages/Shopping';
import Profile from './pages/Profile';
import HaCallback from './pages/HaCallback';
import ProtectedRoute from './components/ProtectedRoute';


function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        {/* Ritorno dal login di Home Assistant: fuori dalle pagine protette, puo' arrivare senza sessione */}
        <Route path="/ha-callback" element={<HaCallback />} />

        {/* Authenticated Routes wrapped in ProtectedRoute (checks localStorage token) */}
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/transactions" element={<Transactions />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/budgets" element={<Budgets />} />
          <Route path="/recurring" element={<RecurringTransactions />} />
          <Route path="/goals" element={<SavingsGoals />} />
          <Route path="/groceries" element={<Groceries />} />
          <Route path="/shopping" element={<Shopping />} />
          <Route path="/comparisons" element={<Comparisons />} />
          <Route path="/categories" element={<Categories />} />
          <Route path="/import" element={<ImportTransactions />} />
          <Route path="/profile" element={<Profile />} />
        </Route>

        {/* Default redirect */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </Router>
  );
}

export default App;
