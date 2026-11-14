import React from 'react';
import { Tag } from 'lucide-react';
import { DynamicIcon, iconNames } from 'lucide-react/dynamic';

// Icona scelta nella categoria (nome Lucide, es. "shopping-cart"); se il nome non esiste, un'etichetta
const CategoryIcon = ({ name, ...props }) =>
  iconNames.includes(name) ? <DynamicIcon name={name} fallback={() => <Tag {...props} />} {...props} /> : <Tag {...props} />;

export default CategoryIcon;
