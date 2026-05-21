import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from './components/ui/AppLayout';
import DashboardPage from './pages/DashboardPage';
import DataProductsPage from './pages/DataProductsPage';
import DataProductDetailPage from './pages/DataProductDetailPage';
import DatasetsPage from './pages/DatasetsPage';
import DatasetDetailPage from './pages/DatasetDetailPage';
import DistributionDetailPage from './pages/DistributionDetailPage';
import LineagePage from './pages/LineagePage';
import HarvestPage from './pages/admin/HarvestPage';
import HarvestSourceDetailPage from './pages/admin/HarvestSourceDetailPage';
import HarvestRunDetailPage from './pages/admin/HarvestRunDetailPage';
import DomainsPage from './pages/admin/DomainsPage';
import UsersPage from './pages/admin/UsersPage';
import SettingsPage from './pages/admin/SettingsPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/default" replace />,
  },
  {
    path: '/:tenant',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'data-products', element: <DataProductsPage /> },
      { path: 'data-products/:id', element: <DataProductDetailPage /> },
      { path: 'datasets', element: <DatasetsPage /> },
      { path: 'datasets/:datasetId/distributions/:id', element: <DistributionDetailPage /> },
      { path: 'datasets/:id', element: <DatasetDetailPage /> },
      { path: 'lineage', element: <LineagePage /> },
      { path: 'admin/harvest', element: <HarvestPage /> },
      { path: 'admin/harvest/sources/:id', element: <HarvestSourceDetailPage /> },
      { path: 'admin/harvest/runs/:id', element: <HarvestRunDetailPage /> },
      { path: 'admin/domains', element: <DomainsPage /> },
      { path: 'admin/users', element: <UsersPage /> },
      { path: 'admin/settings', element: <SettingsPage /> },
    ],
  },
]);
