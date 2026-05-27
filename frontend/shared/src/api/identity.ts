import { get } from './client';
import type { User } from '../types/identity';

export const userApi = {
  list: () => get<User[]>('/api/v1/users'),
  get: (id: string) => get<User>(`/api/v1/users/${id}`),
};
