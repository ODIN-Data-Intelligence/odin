export interface User {
  id: string;
  tenantId: string;
  email: string;
  firstName?: string;
  lastName?: string;
  active: boolean;
  roles: string[];
  permissions: string[];
  createdAt: string;
  keycloakUserId?: string;
}

export interface UserInviteRequest {
  email: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
}
