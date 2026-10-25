import React from 'react';
import {createRoot} from 'react-dom/client';
import {App} from './App';
import {AppearanceProvider} from './Appearance';
import './styles.css';
createRoot(document.getElementById('root')!).render(<React.StrictMode><AppearanceProvider><App/></AppearanceProvider></React.StrictMode>);
