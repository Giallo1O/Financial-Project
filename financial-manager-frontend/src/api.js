import { getUserInfo, getToken, removeAuth } from './auth';

const DASHBOARD_API = import.meta.env.VITE_DASHBOARD_API_URL || 'http://localhost:8081';
const TRANSACTION_API = import.meta.env.VITE_TRANSACTION_API_URL || 'http://localhost:8080';
const USER_API = import.meta.env.VITE_USER_API_URL || 'http://localhost:8082';

// ─── Internal Helpers ──────────────────────────────────────────────────────

/**
 * Custom fetch wrapper that:
 * 1. Attaches Authorization header if token exists
 * 2. Handles 401 Unauthorized by clearing local auth and redirecting to /login
 * 3. Throws error for other non-ok responses
 */
export async function authenticatedFetch(url, options = {}) {
  const token = getToken();
  const headers = {
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(url, { ...options, headers });

  if (response.status === 401) {
    console.warn('[api] 401 Unauthorized detected. Redirecting to login...');
    removeAuth();
    // Redirect using window.location for a hard reset to the login state
    window.location.href = '/login';
    // Return a promise that never resolves to stop further execution in the component
    return new Promise(() => {});
  }

  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }

  return response;
}

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

export function getCurrentUserId() {
  const info = getUserInfo();
  if (!info || !info.userId) {
    throw new Error('User not authenticated');
  }
  return info.userId;
}

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
 */
export async function fetchCategories() {
  const userId = getCurrentUserId();
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/categories?userId=${encodeURIComponent(userId)}`);
  return await response.json();
}

export async function createCategory(categoryData) {
  const payload = {
    ...categoryData,
    userId: getCurrentUserId(),
  };

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/categories`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  return await response.json();
}

// ─── Transactions ──────────────────────────────────────────────────────────

/**
 * Fetches all transactions for the current user.
 * If month and year are provided, returns only that month's data.
 * Resolves categoryId → category name using the category list.
 */
export async function fetchTransactions({ month, year } = {}) {
  const userId = getCurrentUserId();
  let url = `${TRANSACTION_API}/api/transactions?userId=${encodeURIComponent(userId)}`;
  if (month != null && year != null) {
    url += `&month=${month}&year=${year}`;
  }

  const [txResponse, categories] = await Promise.all([
    authenticatedFetch(url),
    fetchCategories(),
  ]);

  const data = await txResponse.json();

  // Build id→name map for fast lookup
  const categoryMap = Object.fromEntries(categories.map(c => [c.id, c.name]));
  return mapTransactions(data, categoryMap);
}

export async function createTransaction(transactionData) {
  const payload = {
    description: transactionData.description || '',
    amount: transactionData.amount,
    type: transactionData.type ? transactionData.type.toUpperCase() : 'USCITA',
    categoryId: transactionData.categoryId,  // UUID from the category select
    userId: getCurrentUserId(),
  };

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

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
  await authenticatedFetch(`${TRANSACTION_API}/api/transactions/${id}`, {
    method: 'DELETE',
  });
}

export async function parseCsv(file) {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions/parse-csv`, {
    method: 'POST',
    body: formData,
  });
  return await response.json();
}

export async function bulkImportTransactions(transactions) {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions/bulk-import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(transactions),
  });
  return response;
}

// ─── Dashboard ────────────────────────────────────────────────────────────

export async function fetchDashboardStats() {
  const userId = getCurrentUserId();
  const response = await authenticatedFetch(`${DASHBOARD_API}/api/dashboard/stats?userId=${encodeURIComponent(userId)}`);
  return await response.json();
}
