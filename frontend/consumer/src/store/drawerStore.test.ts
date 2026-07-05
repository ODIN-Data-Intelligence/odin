import { describe, it, expect, beforeEach } from 'vitest';
import { useDrawerStore } from './drawerStore';

beforeEach(() => {
  useDrawerStore.setState({ openDatasetId: null, activeTab: 'overview' });
});

describe('useDrawerStore', () => {
  describe('initial state', () => {
    it('should have null openDatasetId', () => {
      expect(useDrawerStore.getState().openDatasetId).toBeNull();
    });

    it('should have overview as default activeTab', () => {
      expect(useDrawerStore.getState().activeTab).toBe('overview');
    });
  });

  describe('openDataset', () => {
    it('should set openDatasetId', () => {
      useDrawerStore.getState().openDataset('ds-42');
      expect(useDrawerStore.getState().openDatasetId).toBe('ds-42');
    });

    it('should default to overview tab', () => {
      useDrawerStore.getState().openDataset('ds-42');
      expect(useDrawerStore.getState().activeTab).toBe('overview');
    });

    it('should accept an explicit tab', () => {
      useDrawerStore.getState().openDataset('ds-42', 'lineage');
      expect(useDrawerStore.getState().activeTab).toBe('lineage');
    });

    it('should support all tab types', () => {
      const tabs = ['overview', 'distributions', 'schema', 'lineage', 'access'] as const;
      for (const tab of tabs) {
        useDrawerStore.getState().openDataset('x', tab);
        expect(useDrawerStore.getState().activeTab).toBe(tab);
      }
    });
  });

  describe('closeDrawer', () => {
    it('should set openDatasetId to null', () => {
      useDrawerStore.setState({ openDatasetId: 'ds-1' });
      useDrawerStore.getState().closeDrawer();
      expect(useDrawerStore.getState().openDatasetId).toBeNull();
    });

    it('should leave activeTab unchanged', () => {
      useDrawerStore.setState({ openDatasetId: 'ds-1', activeTab: 'schema' });
      useDrawerStore.getState().closeDrawer();
      expect(useDrawerStore.getState().activeTab).toBe('schema');
    });
  });

  describe('setTab', () => {
    it('should update the active tab', () => {
      useDrawerStore.getState().setTab('schema');
      expect(useDrawerStore.getState().activeTab).toBe('schema');
    });

    it('should not change openDatasetId', () => {
      useDrawerStore.setState({ openDatasetId: 'ds-99' });
      useDrawerStore.getState().setTab('lineage');
      expect(useDrawerStore.getState().openDatasetId).toBe('ds-99');
    });
  });
});
