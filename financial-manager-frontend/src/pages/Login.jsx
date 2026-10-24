import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Wallet, LogIn, Lock, User, UserPlus } from 'lucide-react';
import { loginUser, registerUser } from '../api';
import { setAuth } from '../auth';
import './Login.css';

const Login = () => {
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      if (mode === 'login') {
        const data = await loginUser(username, password);
        setAuth(data.token, { username: data.username, userId: data.userId });
        navigate('/dashboard');
      } else {
        const data = await registerUser(username, password);
        setAuth(data.token, { username: data.username, userId: data.userId });
        navigate('/dashboard');
      }
    } catch (err) {
      setError(err.message || 'Si è verificato un errore. Riprova.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      <div className="glass-panel login-card">
        <div className="login-header">
          <div className="logo-container">
            <Wallet size={48} className="logo-icon" />
          </div>
          <h2>{mode === 'login' ? 'Bentornato' : 'Crea un account'}</h2>
          <p className="text-muted">
            {mode === 'login'
              ? 'Inserisci le credenziali per accedere'
              : 'Registrati per iniziare a gestire le tue finanze'}
          </p>
        </div>

        {error && (
          <div className="login-error">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label>Username</label>
            <div className="input-wrapper">
              <User size={18} className="input-icon" />
              <input
                type="text"
                placeholder="il_tuo_username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="username"
              />
            </div>
          </div>

          <div className="form-group">
            <label>Password</label>
            <div className="input-wrapper">
              <Lock size={18} className="input-icon" />
              <input
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary login-button"
            disabled={isLoading}
          >
            {isLoading ? 'Attendi...' : (
              <>
                <span>{mode === 'login' ? 'Accedi' : 'Registrati'}</span>
                {mode === 'login' ? <LogIn size={20} /> : <UserPlus size={20} />}
              </>
            )}
          </button>
        </form>

        <div className="login-switch">
          {mode === 'login' ? (
            <p className="text-muted">
              Non hai un account?{' '}
              <button className="link-btn" onClick={() => { setMode('register'); setError(''); }}>
                Registrati
              </button>
            </p>
          ) : (
            <p className="text-muted">
              Hai già un account?{' '}
              <button className="link-btn" onClick={() => { setMode('login'); setError(''); }}>
                Accedi
              </button>
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default Login;
