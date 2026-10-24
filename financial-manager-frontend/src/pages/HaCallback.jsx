import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle, AlertTriangle, Loader } from 'lucide-react';
import { finishHomeAssistantLink } from '../api';
import { isAuthenticated } from '../auth';
import './Profile.css';

/** Pagina su cui Home Assistant rimanda dopo il login (redirect_uri). Funziona anche senza sessione FinManager. */
const HaCallback = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [state, setState] = useState({ phase: 'working', message: '' });
  const done = useRef(false);

  useEffect(() => {
    if (done.current) return; // in sviluppo React esegue gli effetti due volte: il codice e' monouso
    done.current = true;
    const code = params.get('code');
    const st = params.get('state');
    const fail = (message) => setState({ phase: 'error', message });
    if (params.get('error') || !code || !st) {
      fail('Accesso a Home Assistant annullato o non concesso.');
      return;
    }
    finishHomeAssistantLink(st, code).then(() => {
      if (window.opener && !window.opener.closed) {
        window.opener.postMessage({ type: 'ha-linked' }, window.location.origin);
        window.close();
      }
      // finestra rimasta aperta (o app installata): torno al Profilo se c'e' la sessione
      setState({ phase: 'ok', message: '' });
      if (isAuthenticated()) setTimeout(() => navigate('/profile', { replace: true }), 1200);
    }).catch((err) => fail(err.message));
  }, [params, navigate]);

  return (
    <div className="ha-callback">
      <div className="glass-panel">
        {state.phase === 'working' && <><Loader size={32} className="spin" /><p>Collegamento con Home Assistant…</p></>}
        {state.phase === 'ok' && <><CheckCircle size={36} className="text-success" /><h3>Account collegato</h3><p className="text-muted">Puoi chiudere questa finestra.</p></>}
        {state.phase === 'error' && (
          <>
            <AlertTriangle size={36} className="text-danger" />
            <h3>Collegamento non riuscito</h3>
            <p className="text-muted">{state.message}</p>
            {isAuthenticated() && <button className="btn btn-primary" onClick={() => navigate('/profile', { replace: true })}>Torna al Profilo</button>}
          </>
        )}
      </div>
    </div>
  );
};

export default HaCallback;
