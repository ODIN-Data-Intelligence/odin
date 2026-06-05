import { get, post, put, patch, del } from './client';
import type {
  Bookmark,
  BookmarkCollection,
  BookmarkCollectionRequest,
  BookmarkRequest,
  BookmarkPatchRequest,
} from '../types/bookmarks';

export const bookmarkCollectionApi = {
  list: () =>
    get<BookmarkCollection[]>('/api/v1/bookmark-collections'),

  create: (body: BookmarkCollectionRequest) =>
    post<BookmarkCollection>('/api/v1/bookmark-collections', body),

  update: (id: string, body: BookmarkCollectionRequest) =>
    put<BookmarkCollection>(`/api/v1/bookmark-collections/${id}`, body),

  delete: (id: string) =>
    del<void>(`/api/v1/bookmark-collections/${id}`),
};

export const bookmarkApi = {
  list: (collectionId?: string) =>
    get<Bookmark[]>(collectionId
      ? `/api/v1/bookmarks?collectionId=${collectionId}`
      : '/api/v1/bookmarks'),

  check: (datasetId: string) =>
    get<Bookmark>(`/api/v1/bookmarks/dataset/${datasetId}`),

  create: (body: BookmarkRequest) =>
    post<Bookmark>('/api/v1/bookmarks', body),

  patch: (id: string, body: BookmarkPatchRequest) =>
    patch<Bookmark>(`/api/v1/bookmarks/${id}`, body),

  delete: (id: string) =>
    del<void>(`/api/v1/bookmarks/${id}`),
};
