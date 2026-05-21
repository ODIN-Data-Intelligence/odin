import { useRef } from 'react';
import { useOutletContext } from 'react-router-dom';
import SearchBar from '../components/SearchBar';
import TrendingDatasets from '../components/TrendingDatasets';
import RecentlyViewed from '../components/RecentlyViewed';
import DatasetDetailDrawer from '../components/DatasetDetailDrawer';
import { useDrawerStore } from '../store/drawerStore';

export default function HomePage() {
  const localRef = useRef<HTMLInputElement>(null);
  const { openDatasetId } = useDrawerStore();

  return (
    <div className="flex h-screen overflow-hidden">
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-3xl mx-auto px-6 py-16">
          <div className="text-center mb-10">
            <h1 className="text-4xl font-bold text-gray-900 mb-3">Find Your Data</h1>
            <p className="text-gray-500 text-lg">Search thousands of datasets, data products, and schemas</p>
          </div>
          <SearchBar ref={localRef} large />
          <p className="text-center text-xs text-gray-400 mt-3">Press <kbd className="px-1.5 py-0.5 bg-gray-100 rounded">/</kbd> to focus search · <kbd className="px-1.5 py-0.5 bg-gray-100 rounded">⌘K</kbd> to open AI chat</p>

          <div className="mt-12 space-y-8">
            <RecentlyViewed />
            <TrendingDatasets />
          </div>
        </div>
      </div>

      {openDatasetId && (
        <DatasetDetailDrawer />
      )}
    </div>
  );
}
