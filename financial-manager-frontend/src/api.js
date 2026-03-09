const DASHBOARD_API = import.meta.env.VITE_DASHBOARD_API_URL || 'http://localhost:8081';
const TRANSACTION_API = import.meta.env.VITE_TRANSACTION_API_URL || 'http://localhost:8080';
const USER_API = import.meta.env.VITE_USER_API_URL || 'http://localhost:8082';

// ─── Auth ─────────────────────────────────────────────────────────────────

export async function loginUser(username, password) {
  const response = await fetch(`${USER_API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  if (!response.ok) throw new Error('Invalid username or password');
  return response.json(); // { token, username, userId }
}

export async function registerUser(username, password) {
  const response = await fetch(`${USER_API}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  if (response.status === 409) throw new Error('Username already taken');
  if (!response.ok) throw new Error('Registration failed');
  return response.json();
}

// ─── Current user ─────────────────────────────────────────────────────────

import { getUserInfo } from './auth';

const SUPERUSER_ID = 'default-user';

export function getCurrentUserId() {
  const info = getUserInfo();
  // superuser bypass keeps the hardcoded id so existing demo data still works
  if (!info || info.userId === 'superuser-id') return SUPERUSER_ID;
  return info.userId;
}

// ─── Mock fallback data ────────────────────────────────────────────────────

const MOCK_TRANSACTIONS = [
  { id: '1', type: 'entrata',  amount: 2500, category: 'Stipendio',       categoryId: null, description: 'Monthly salary',  date: '2026-03-01' },
  { id: '2', type: 'uscita',   amount: 800,  category: 'Affitto',         categoryId: null, description: 'Rent payment',    date: '2026-03-02' },
  { id: '3', type: 'uscita',   amount: 200,  category: 'Cibo',            categoryId: null, description: 'Groceries',       date: '2026-03-05' },
  { id: '4', type: 'uscita',   amount: 150,  category: 'Trasporti',       categoryId: null, description: 'Transport',       date: '2026-03-07' },
  { id: '5', type: 'uscita',   amount: 100,  category: 'Intrattenimento', categoryId: null, description: 'Streaming subs',  date: '2026-03-08' },
];

const MOCK_CATEGORIES = [
  { id: 'mock-cat-1', name: 'Affitto',         color: '#6366f1', icon: 'home' },
  { id: 'mock-cat-2', name: 'Cibo',            color: '#f59e0b', icon: 'utensils' },
  { id: 'mock-cat-3', name: 'Trasporti',       color: '#10b981', icon: 'car' },
  { id: 'mock-cat-4', name: 'Intrattenimento', color: '#ec4899', icon: 'music' },
  { id: 'mock-cat-5', name: 'Stipendio',       color: '#22c55e', icon: 'briefcase' },
  { id: 'mock-cat-6', name: 'Altro',           color: '#94a3b8', icon: 'tag' },
];

const MOCK_DASHBOARD_STATS = [
  { categoryId: 'Affitto',         totalAmount: 800,  transactionCount: 1 },
  { categoryId: 'Cibo',            totalAmount: 200,  transactionCount: 3 },
  { categoryId: 'Trasporti',       totalAmount: 150,  transactionCount: 4 },
  { categoryId: 'Intrattenimento', totalAmount: 100,  transactionCount: 2 },
  { categoryId: 'Altro',           totalAmount: 75,   transactionCount: 2 },
];

// ─── Helper: mappa categoryId (UUID) → nome ───────────────────────────────

function mapTransactions(list, categoryMap) {
  return list.map(t => ({
    id: t.id,
    type: t.type ? t.type.toLowerCase() : '',
    amount: Number(t.amount),
    categoryId: t.categoryId,
    category: categoryMap[t.categoryId] ?? t.categoryId ?? '—',
    description: t.description,
    date: t.createdAt ? t.createdAt.split('T')[0] : '',
  }));
}

// ─── Categories ───────────────────────────────────────────────────────────

/**
 * Returns array of { id, name, color, icon }
 * Falls back to MOCK_CATEGORIES if backend is unavailable.
 */
export async function fetchCategories() {
  try {
    const userId = getCurrentUserId();
    const response = await fetch(`${TRANSACTION_API}/api/categories?userId=${encodeURIComponent(userId)}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    return data;
  } catch (err) {
    console.warn('[api] fetchCategories fallback to mock data:', err.message);
    return MOCK_CATEGORIES;
  }
}
export async function createCategory(categoryData) {
  const payload = {
    ...categoryData,
    userId: getCurrentUserId(),
  };

  const response = await fetch(`${TRANSACTION_API}/api/categories`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) throw new Error(`Failed to create category: HTTP ${response.status}`);
  return response.json();
}

// ─── Transactions ──────────────────────────────────────────────────────────

/**
 * Fetches all transactions for the current user.
 * If month and year are provided, returns only that month's data.
 * Resolves categoryId → category name using the category list.
 */
export async function fetchTransactions({ month, year } = {}) {
  try {
    const userId = getCurrentUserId();
    let url = `${TRANSACTION_API}/api/transactions?userId=${encodeURIComponent(userId)}`;
    if (month != null && year != null) {
      url += `&month=${month}&year=${year}`;
    }

    const [txResponse, categories] = await Promise.all([
      fetch(url),
      fetchCategories(),
    ]);

    if (!txResponse.ok) throw new Error(`HTTP ${txResponse.status}`);
    const data = await txResponse.json();

    // Build id→name map for fast lookup
    const categoryMap = Object.fromEntries(categories.map(c => [c.id, c.name]));
    return mapTransactions(data, categoryMap);
  } catch (err) {
    console.warn('[api] fetchTransactions fallback to mock data:', err.message);
    return month != null
      ? MOCK_TRANSACTIONS.filter(t => {
          const d = new Date(t.date);
          return d.getMonth() + 1 === month && d.getFullYear() === year;
        })
      : MOCK_TRANSACTIONS;
  }
}

export async function createTransaction(transactionData) {
  const payload = {
    description: transactionData.description || '',
    amount: transactionData.amount,
    type: transactionData.type ? transactionData.type.toUpperCase() : 'USCITA',
    categoryId: transactionData.categoryId,  // UUID from the category select
    userId: getCurrentUserId(),
  };

  const response = await fetch(`${TRANSACTION_API}/api/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) throw new Error(`Failed to create transaction: HTTP ${response.status}`);
  const t = await response.json();

  const categories = await fetchCategories();
  const categoryMap = Object.fromEntries(categories.map(c => [c.id, c.name]));

  return {
    id: t.id,
    type: t.type ? t.type.toLowerCase() : '',
    amount: Number(t.amount),
    categoryId: t.categoryId,
    category: categoryMap[t.categoryId] ?? t.categoryId ?? '—',
    description: t.description,
    date: t.createdAt ? t.createdAt.split('T')[0] : new Date().toISOString().split('T')[0],
  };
}

export async function deleteTransaction(id) {
  const response = await fetch(`${TRANSACTION_API}/api/transactions/${id}`, {
    method: 'DELETE',
  });
  if (!response.ok && response.status !== 204) {
    throw new Error(`Failed to delete transaction: HTTP ${response.status}`);
  }
}

// ─── Dashboard ────────────────────────────────────────────────────────────

export async function fetchDashboardStats() {
  try {
    const userId = getCurrentUserId();
    const response = await fetch(`${DASHBOARD_API}/api/dashboard/stats?userId=${encodeURIComponent(userId)}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  } catch (err) {
    console.warn('[api] fetchDashboardStats fallback to mock data:', err.message);
    return MOCK_DASHBOARD_STATS;
  }
}
