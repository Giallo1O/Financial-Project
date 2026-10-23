import React, { createContext, useContext, useState, useEffect } from 'react';

const PrivacyContext = createContext();

export const PrivacyProvider = ({ children }) => {
  const [privacyMode, setPrivacyMode] = useState(() => {
    try {
      return localStorage.getItem('finmanager_privacy_mode') === 'true';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('finmanager_privacy_mode', privacyMode);
    } catch (e) {
      console.error('Failed to save privacy mode state', e);
    }

    if (privacyMode) {
      document.body.classList.add('privacy-active');
    } else {
      document.body.classList.remove('privacy-active');
    }
  }, [privacyMode]);

  const togglePrivacyMode = () => {
    setPrivacyMode(prev => !prev);
  };

  return (
    <PrivacyContext.Provider value={{ privacyMode, togglePrivacyMode, setPrivacyMode }}>
      {children}
    </PrivacyContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const usePrivacy = () => {
  const context = useContext(PrivacyContext);
  if (!context) {
    throw new Error('usePrivacy must be used within a PrivacyProvider');
  }
  return context;
};
