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
  if (!response.ok) throw new Error('Username o password non corretti');
  return response.json(); // { token, username, userId }
}

export async function registerUser(username, password) {
  const response = await fetch(`${USER_API}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  if (response.status === 409) throw new Error('Username già in uso');
  if (!response.ok) throw new Error('Registrazione non riuscita');
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
      category: categoryInfo ? categoryInfo.name : 'Senza categoria',
      categoryColor: categoryInfo ? categoryInfo.color : '#6b7280', // default gray
      categoryTags: categoryInfo ? (categoryInfo.tags || []) : [],
      isFixed: categoryInfo ? Boolean(categoryInfo.fixed) : false,
      description: t.description,
      accountId: t.accountId || null,
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
    accountId: transactionData.accountId || null,
  };

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  return toTransactionView(await response.json());
}

export async function updateTransaction(id, transactionData) {
  const payload = {
    description: transactionData.description || '',
    amount: transactionData.amount,
    type: transactionData.type ? transactionData.type.toUpperCase() : 'USCITA',
    categoryId: transactionData.categoryId || null,
    transactionDate: transactionData.date,
    accountId: transactionData.accountId || null,
  };
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return toTransactionView(await response.json());
}

async function toTransactionView(t) {
  const categories = await fetchCategories();
  return mapTransactions([t], Object.fromEntries(categories.map(c => [c.id, c])))[0];
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

/** learn = true: il server ricorda le categorie scelte come regole per i prossimi import. */
export async function bulkImportTransactions(transactions, learn = false, accountId = null) {
  const userId = getCurrentUserId();
  const payload = transactions.map(t => ({
    ...t,
    userId,
    // Ensure categoryId is a valid UUID or null (not empty string)
    categoryId: t.categoryId === '' ? null : t.categoryId
  }));

  const response = await authenticatedFetch(`${TRANSACTION_API}/api/transactions/bulk-import?learn=${learn}${accountId ? `&accountId=${accountId}` : ''}`, {
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

// ─── Regole di categorizzazione automatica ────────────────────────────────

export async function fetchCategoryRules() {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/category-rules`);
  return await response.json();
}

/** Crea la regola, o ne cambia la categoria se la parola chiave esiste già. */
export async function saveCategoryRule(keyword, categoryId) {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/category-rules`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ keyword, categoryId }),
  });
  return await response.json();
}

export async function deleteCategoryRule(id) {
  await authenticatedFetch(`${TRANSACTION_API}/api/category-rules/${id}`, { method: 'DELETE' });
}

/** Crea regole dalle transazioni già categorizzate; restituisce { created }. */
export async function learnCategoryRulesFromHistory() {
  const response = await authenticatedFetch(`${TRANSACTION_API}/api/category-rules/learn-from-history`, { method: 'POST' });
  return await response.json();
}

// ─── Lista della spesa ────────────────────────────────────────────────────

const SHOP = `${TRANSACTION_API}/api/shopping-lists`;
const json = (method, body) => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

export async function fetchShoppingLists() {
  return (await authenticatedFetch(SHOP)).json();
}

export async function createShoppingList(name) {
  return (await authenticatedFetch(SHOP, json('POST', { name }))).json();
}

/** Il proprietario elimina la lista, gli altri ne escono. */
export async function deleteOrLeaveShoppingList(id) {
  await authenticatedFetch(`${SHOP}/${id}`, { method: 'DELETE' });
}

/** Restituisce { code }: il codice con cui un'altra persona entra nella lista. */
export async function createShoppingInvite(id) {
  return (await authenticatedFetch(`${SHOP}/${id}/invite`, { method: 'POST' })).json();
}

export async function revokeShoppingInvite(id) {
  await authenticatedFetch(`${SHOP}/${id}/invite`, { method: 'DELETE' });
}

export async function joinShoppingList(code) {
  return (await authenticatedFetch(`${SHOP}/join`, json('POST', { code }))).json();
}

export async function fetchShoppingItems(listId) {
  return (await authenticatedFetch(`${SHOP}/${listId}/items`)).json();
}

export async function addShoppingItem(listId, { name, quantity, unit }) {
  return (await authenticatedFetch(`${SHOP}/${listId}/items`, json('POST', { name, quantity, unit }))).json();
}

/** checked e/o quantity; i campi omessi non cambiano. */
export async function updateShoppingItem(listId, itemId, changes) {
  return (await authenticatedFetch(`${SHOP}/${listId}/items/${itemId}`, json('PUT', changes))).json();
}

export async function deleteShoppingItem(listId, itemId) {
  await authenticatedFetch(`${SHOP}/${listId}/items/${itemId}`, { method: 'DELETE' });
}

/** Toglie dalla lista le voci già prese. */
export async function clearCheckedShoppingItems(listId) {
  await authenticatedFetch(`${SHOP}/${listId}/items`, { method: 'DELETE' });
}

/** Prodotti che compri di solito (dai tuoi scontrini), con "due" = probabilmente da ricomprare. */
export async function fetchShoppingSuggestions() {
  return (await authenticatedFetch(`${SHOP}/suggestions`)).json();
}

// ─── Conti e giroconti ─────────────────────────────────────────────────────

const ACC = `${TRANSACTION_API}/api`;
const jsonPost = (url, method, body) => authenticatedFetch(url, {
  method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
});

/** { accounts: [{id,name,type,color,initialBalance,balance,archived}], unassigned, hasUnassigned, total } */
export async function fetchAccounts() {
  return (await authenticatedFetch(`${ACC}/accounts`)).json();
}

export async function createAccount(data) {
  return (await jsonPost(`${ACC}/accounts`, 'POST', data)).json();
}

export async function updateAccount(id, data) {
  return (await jsonPost(`${ACC}/accounts/${id}`, 'PUT', data)).json();
}

export async function deleteAccount(id) {
  await authenticatedFetch(`${ACC}/accounts/${id}`, { method: 'DELETE' });
}

/** Mette su un conto tutti i movimenti che non ne hanno uno; restituisce quanti. */
export async function assignUnassignedToAccount(accountId) {
  return (await jsonPost(`${ACC}/accounts/assign-unassigned`, 'POST', { accountId })).json();
}

export async function fetchTransfers() {
  return (await authenticatedFetch(`${ACC}/transfers`)).json();
}

export async function createTransfer(data) {
  return (await jsonPost(`${ACC}/transfers`, 'POST', data)).json();
}

export async function deleteTransfer(id) {
  await authenticatedFetch(`${ACC}/transfers/${id}`, { method: 'DELETE' });
}

// ─── Spese in due (gruppi con codice) ─────────────────────────────────────

const GRP = `${TRANSACTION_API}/api/groups`;

export async function fetchGroups() {
  return (await authenticatedFetch(GRP)).json();
}

export async function createGroup(name) {
  return (await jsonPost(GRP, 'POST', { name })).json();
}

export async function joinGroup(code) {
  return (await jsonPost(`${GRP}/join`, 'POST', { code })).json();
}

export async function createGroupInvite(id) {
  return (await authenticatedFetch(`${GRP}/${id}/invite`, { method: 'POST' })).json();
}

export async function revokeGroupInvite(id) {
  await authenticatedFetch(`${GRP}/${id}/invite`, { method: 'DELETE' });
}

export async function leaveGroup(id) {
  await authenticatedFetch(`${GRP}/${id}`, { method: 'DELETE' });
}

/** { group, balances, debts: [{from,to,amount}], expenses, settlements } */
export async function fetchGroupSummary(id) {
  return (await authenticatedFetch(`${GRP}/${id}/summary`)).json();
}

export async function addGroupExpense(id, expense) {
  return (await jsonPost(`${GRP}/${id}/expenses`, 'POST', expense)).json();
}

export async function deleteGroupExpense(id, expenseId) {
  await authenticatedFetch(`${GRP}/${id}/expenses/${expenseId}`, { method: 'DELETE' });
}

export async function addGroupSettlement(id, settlement) {
  return (await jsonPost(`${GRP}/${id}/settlements`, 'POST', settlement)).json();
}

export async function deleteGroupSettlement(id, settlementId) {
  await authenticatedFetch(`${GRP}/${id}/settlements/${settlementId}`, { method: 'DELETE' });
}

// ─── Previsione e avvisi ──────────────────────────────────────────────────

export async function fetchForecast() {
  return (await authenticatedFetch(`${TRANSACTION_API}/api/forecast`)).json();
}

/** [{ type, severity: 'danger'|'warning'|'info', message, link }], i più gravi per primi. */
export async function fetchAlerts() {
  return (await authenticatedFetch(`${TRANSACTION_API}/api/alerts`)).json();
}
