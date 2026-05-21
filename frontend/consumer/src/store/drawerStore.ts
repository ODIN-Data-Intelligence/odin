import { create } from 'zustand';

type DrawerTab = 'overview' | 'distributions' | 'schema' | 'lineage' | 'access';

interface DrawerState {
  openDatasetId: string | null;
  activeTab: DrawerTab;
  openDataset: (id: string, tab?: DrawerTab) => void;
  closeDrawer: () => void;
  setTab: (tab: DrawerTab) => void;
}

export const useDrawerStore = create<DrawerState>((set) => ({
  openDatasetId: null,
  activeTab: 'overview',
  openDataset: (id, tab = 'overview') => set({ openDatasetId: id, activeTab: tab }),
  closeDrawer: () => set({ openDatasetId: null }),
  setTab: (tab) => set({ activeTab: tab }),
}));
