import { createBrowserRouter } from 'react-router-dom';
import AppShell from './components/AppShell';
import HomePage from './pages/HomePage';
import SearchPage from './pages/SearchPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'datasets/:id', element: <SearchPage /> },
      { path: 'data-products/:id', element: <SearchPage /> },
    ],
  },
]);
