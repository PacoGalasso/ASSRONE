// features/profile/profile.model.ts
export type Role = 'ADMIN' | 'USER';

export interface UserProfile {
  id: number;
  email: string;
  username: string;
  firstName: string;
  lastName: string;
  role: string;
  createdAt: string;
}

export interface RoleChangeRequest {
  role: Role;
}

export interface UpdateProfileRequest {
  username: string;
  firstName: string;
  lastName: string;
  email: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
