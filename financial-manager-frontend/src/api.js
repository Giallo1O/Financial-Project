import { getUserInfo, getToken, removeAuth } from './auth';

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
    return new Promise(() => { });
  }

  if (!response.ok) {
    // Se il server spiega il motivo (campo "message") lo uso, altrimenti il codice HTTP
    const body = await response.json().catch(() => null);
    const error = new Error(body?.message || `HTTP ${response.status}`);
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
  return list.map(t => {
    const categoryInfo = categoryMap[t.categoryId];
    return {
      id: t.id,
      type: t.type ? t.type.toLowerCase() : '',
      amount: Number(t.amount),
      categoryId: t.categoryId,
      category: categoryInfo ? categoryInfo.name : t.categoryId ?? '—',
      categoryColor: categoryInfo ? categoryInfo.color : '#6b7280', // default gray
      categoryTags: categoryInfo ? (categoryInfo.tags || []) : [],
      isFixed: categoryInfo ? Boolean(categoryInfo.fixed) : false,
      description: t.description,
      date: t.transactionDate || '',
    };
  });
}

// ─── Categories ───────────────────────────────────────────────────────────

/**
 * Returns array of { id, name, color, icon, expense, budget, fixed, tags }
 */
export async function fetchCategories() {
  const userId = getCurrentUserId();
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/categories?userId=${encodeURIComponent(userId)}`);
  return await response.json();
}

export async function createCategory(categoryData) {
  const payload = {
    ...categoryData,
    budget: categoryData.budget ? Number(categoryData.budget) : 0,
    fixed: Boolean(categoryData.fixed),
    userId: getCurrentUserId(),
  };

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/categories`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  return await response.json();
}

export async function updateCategory(id, categoryData) {
  const payload = {
    ...categoryData,
    budget: categoryData.budget ? Number(categoryData.budget) : 0,
    fixed: Boolean(categoryData.fixed),
    userId: getCurrentUserId(),
  };

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/categories/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  return await response.json();
}

export async function deleteCategory(id) {
  await authenticatedFetch(`${TRANSACTION_API}/api/categories/${id}`, {
    method: 'DELETE',
  });
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

  // Build id→category map for fast lookup
  const categoryMap = Object.fromEntries(categories.map(c => [c.id, c]));
  return mapTransactions(data, categoryMap);
}

export async function createTransaction(transactionData) {
  const payload = {
    description: transactionData.description || '',
    amount: transactionData.amount,
    type: transactionData.type ? transactionData.type.toUpperCase() : 'USCITA',
    categoryId: transactionData.categoryId,  // UUID from the category select
    userId: getCurrentUserId(),
    transactionDate: transactionData.date,
  };

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const t = await response.json();

  const categories = await fetchCategories();
  const categoryMap = Object.fromEntries(categories.map(c => [c.id, c]));
  const categoryInfo = categoryMap[t.categoryId];

  return {
    id: t.id,
    type: t.type ? t.type.toLowerCase() : '',
    amount: Number(t.amount),
    categoryId: t.categoryId,
    category: categoryInfo ? categoryInfo.name : t.categoryId ?? '—',
    categoryColor: categoryInfo ? categoryInfo.color : '#6b7280',
    categoryTags: categoryInfo ? (categoryInfo.tags || []) : [],
    description: t.description,
    date: t.transactionDate || new Date().toISOString().split('T')[0],
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
  const userId = getCurrentUserId();
  const payload = transactions.map(t => ({
    ...t,
    userId,
    // Ensure categoryId is a valid UUID or null (not empty string)
    categoryId: t.categoryId === '' ? null : t.categoryId
  }));

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions/bulk-import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return response;
}

// ─── Savings Goals ────────────────────────────────────────────────────────

export async function fetchSavingsGoals() {
  const userId = getCurrentUserId();
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/savings-goals?userId=${encodeURIComponent(userId)}`);
  return await response.json();
}

export async function createSavingsGoal(goalData) {
  const payload = {
    ...goalData,
    targetAmount: Number(goalData.targetAmount),
    currentAmount: goalData.currentAmount ? Number(goalData.currentAmount) : 0,
    userId: getCurrentUserId(),
  };
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/savings-goals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return await response.json();
}

export async function updateSavingsGoal(id, goalData) {
  const payload = {
    ...goalData,
    targetAmount: Number(goalData.targetAmount),
    currentAmount: goalData.currentAmount != null ? Number(goalData.currentAmount) : undefined,
    userId: getCurrentUserId(),
  };
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/savings-goals/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return await response.json();
}

export async function deleteSavingsGoal(id) {
  await authenticatedFetch(`${TRANSACTION_API}/api/savings-goals/${id}`, {
    method: 'DELETE',
  });
}

export async function depositToSavingsGoal(id, amount) {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/savings-goals/${id}/deposit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: Number(amount) }),
  });
  return await response.json();
}

export async function withdrawFromSavingsGoal(id, amount) {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/savings-goals/${id}/withdraw`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: Number(amount) }),
  });
  return await response.json();
}

// ─── Recurring Transactions ───────────────────────────────────────────────

export async function fetchRecurringTransactions() {
  const userId = getCurrentUserId();
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/recurring-transactions?userId=${encodeURIComponent(userId)}`);
  return await response.json();
}

export async function createRecurringTransaction(data) {
  const payload = {
    ...data,
    amount: Number(data.amount),
    dayOfMonth: Number(data.dayOfMonth) || 1,
    autoConfirm: Boolean(data.autoConfirm),
    userId: getCurrentUserId(),
  };
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/recurring-transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return await response.json();
}

export async function updateRecurringTransaction(id, data) {
  const payload = {
    ...data,
    amount: Number(data.amount),
    dayOfMonth: Number(data.dayOfMonth) || 1,
    autoConfirm: Boolean(data.autoConfirm),
    userId: getCurrentUserId(),
  };
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/recurring-transactions/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return await response.json();
}

export async function deleteRecurringTransaction(id) {
  await authenticatedFetch(`${TRANSACTION_API}/api/recurring-transactions/${id}`, {
    method: 'DELETE',
  });
}

export async function confirmRecurringTransaction(id, date) {
  let url = `${TRANSACTION_API}/api/recurring-transactions/${id}/confirm`;
  if (date) {
    url += `?date=${encodeURIComponent(date)}`;
  }
  const response = await authenticatedFetch(url, {
    method: 'POST',
  });
  return await response.json();
}

export async function processAutoRecurringTransactions() {
  const userId = getCurrentUserId();
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/recurring-transactions/process-auto?userId=${encodeURIComponent(userId)}`, {
    method: 'POST',
  });
  return await response.json();
}

// ─── Scontrini (spesa alimentare) ──────────────────────────────────────────

/** Legge 1-4 foto/PDF dello stesso scontrino con l'AI; restituisce una bozza, non salva nulla. */
export async function scanReceipt(files) {
  const formData = new FormData();
  files.forEach(f => formData.append('file', f));
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/receipts/scan`, {
    method: 'POST',
    body: formData,
  });
  return await response.json();
}

export async function saveReceipt(receipt) {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/receipts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(receipt),
  });
  return await response.json();
}

/** Movimenti gia' presenti (es. importati dalla banca) che potrebbero essere questo scontrino. */
export async function fetchReceiptMatches(date, total) {
  const response = await authenticatedFetch(
    `${TRANSACTION_API}/api/receipts/match?date=${date}&total=${encodeURIComponent(total)}`
  );
  return await response.json();
}

export async function fetchReceipts() {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/receipts`);
  return await response.json();
}

export async function fetchReceipt(id) {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/receipts/${id}`);
  return await response.json();
}

export async function deleteReceipt(id) {
  await authenticatedFetch(`${TRANSACTION_API}/api/receipts/${id}`, { method: 'DELETE' });
}

export async function fetchProducts() {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/receipts/products`);
  return await response.json();
}

export async function fetchProductHistory(name, unit) {
  const response = await authenticatedFetch(
    `${TRANSACTION_API}/api/receipts/products/history?name=${encodeURIComponent(name)}&unit=${encodeURIComponent(unit)}`
  );
  return await response.json();
}

/** from/to in formato yyyy-MM-dd */
export async function fetchFoodSpending(from, to) {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/receipts/spending?from=${from}&to=${to}`);
  return await response.json();
}
