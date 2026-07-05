import { get, post, put, del } from './client';
import type { User, UserInviteRequest } from '../types/identity';

export const userApi = {
  list:            ()                               => get<User[]>('/api/v1/users'),
  get:             (id: string)                     => get<User>(`/api/v1/users/${id}`),
  getByKeycloakId: (kcId: string)                   => get<User>(`/api/v1/users/by-keycloak/${kcId}`),
  invite:     (body: UserInviteRequest)        => post<User>('/api/v1/users/invite', body),
  update:     (id: string, body: UserInviteRequest) => put<User>(`/api/v1/users/${id}`, body),
  activate:   (id: string)                     => post<User>(`/api/v1/users/${id}/activate`, {}),
  deactivate: (id: string)                     => del<void>(`/api/v1/users/${id}`),
};
