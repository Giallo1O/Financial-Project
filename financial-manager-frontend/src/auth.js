// Utility per la gestione del token di autenticazione in localStorage

const TOKEN_KEY = 'fm_auth_token';
const USER_KEY = 'fm_auth_user';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setAuth(token, userInfo) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(userInfo));
}

export function removeAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function getUserInfo() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function isAuthenticated() {
  return !!getToken();
}
