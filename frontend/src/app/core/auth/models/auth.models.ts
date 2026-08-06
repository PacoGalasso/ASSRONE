export interface AuthResponse {
  token: string;
  username: string;
  role: string;
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

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ResendVerificationRequest {
  email: string;
}

// Every account-lifecycle endpoint (forgot-password, reset-password,
// verify-email, resend-verification) returns this same generic shape — see
// the backend's anti-enumeration design (PasswordResetController /
// EmailVerificationController), never a payload that would let the response
// differ based on account existence or verification state.
export interface AccountLifecycleMessageResponse {
  message: string;
}

