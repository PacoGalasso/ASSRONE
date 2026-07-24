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

