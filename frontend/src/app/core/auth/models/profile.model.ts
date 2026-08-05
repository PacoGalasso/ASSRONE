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

// Mirrors backend UserDto (GET /api/users, admin-only) — a distinct shape
// from UserProfileDto/UserProfile above (self-service GET /api/profile):
// this one carries updatedAt/isActive, which the profile endpoint does not.
export interface AdminUserDto {
  id: number;
  email: string;
  username: string;
  firstName: string;
  lastName: string;
  createdAt: string;
  updatedAt: string;
  isActive: boolean;
  role: string;
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
