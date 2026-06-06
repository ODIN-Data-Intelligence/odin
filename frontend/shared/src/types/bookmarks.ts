export interface BookmarkCollection {
  id: string;
  tenantId: string;
  userId: string;
  name: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Bookmark {
  id: string;
  tenantId: string;
  userId: string;
  datasetId: string;
  datasetTitle: string;
  collectionId?: string;
  note?: string;
  createdAt: string;
  updatedAt: string;
}

export interface BookmarkCollectionRequest {
  name: string;
  description?: string;
}

export interface BookmarkRequest {
  datasetId: string;
  datasetTitle: string;
  collectionId?: string;
  note?: string;
}

export interface BookmarkPatchRequest {
  collectionId: string | null;
  note?: string | null;
}
