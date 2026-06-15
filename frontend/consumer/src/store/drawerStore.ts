import { create } from 'zustand';

type DrawerTab = 'overview' | 'distributions' | 'schema' | 'lineage' | 'terms' | 'access' | 'datasets';
type EntityType = 'DATASET' | 'DATA_PRODUCT';

interface DrawerState {
  openDatasetId: string | null;
  openEntityType: EntityType;
  activeTab: DrawerTab;
  openDataset: (id: string, entityType?: EntityType, tab?: DrawerTab) => void;
  closeDrawer: () => void;
  setTab: (tab: DrawerTab) => void;
}

export const useDrawerStore = create<DrawerState>((set) => ({
  openDatasetId: null,
  openEntityType: 'DATASET',
  activeTab: 'overview',
  openDataset: (id, entityType = 'DATASET', tab = 'overview') =>
    set({ openDatasetId: id, openEntityType: entityType, activeTab: tab }),
  closeDrawer: () => set({ openDatasetId: null }),
  setTab: (tab) => set({ activeTab: tab }),
}));
