import React from 'react';
import { Route, Routes } from 'react-router-dom';

import KoilRecordsPage from './pages/koil-records/KoilRecordsPage';

import { ThemeProvider } from './hooks/useTheme';
import { LanguageProvider } from './hooks/useTranslation';
import Layout from './components/Layout';
import NotFound from './pages/NotFound/NotFound';

const RoutesComponent = () => {
  return (
    <LanguageProvider>
      <ThemeProvider>
        <Routes>
        <Route element={<Layout />}>
          <Route index element={<KoilRecordsPage />} />
          <Route path="koil-records" element={<KoilRecordsPage />} />
        </Route>
        <Route path="*" element={<NotFound />} />
        </Routes>
      </ThemeProvider>
    </LanguageProvider>
  );
};

export default RoutesComponent;
