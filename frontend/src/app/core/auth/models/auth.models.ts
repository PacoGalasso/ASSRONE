export interface AuthResponse {
  token: string;
  username: string;
  role: string;
  refreshToken: string;
}

export interface Credentials {
  email: string;
  password: string;
}

export interface User {
  username: string;
  role: string;
  email?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password: string;
}

export interface RegisterResponse {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
}

