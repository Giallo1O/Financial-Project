import React from 'react';
import { usePrivacy } from '../context/PrivacyContext';

export const PrivacyMask = ({ children, className = '', as = 'span' }) => {
  const { privacyMode } = usePrivacy();
  const Tag = as;

  return (
    <Tag
      data-privacy="true"
      className={`privacy-target ${privacyMode ? 'privacy-blur' : ''} ${className}`}
    >
      {children}
    </Tag>
  );
};

export default PrivacyMask;
