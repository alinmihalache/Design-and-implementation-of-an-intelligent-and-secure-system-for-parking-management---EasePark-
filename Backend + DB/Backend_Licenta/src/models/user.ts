// Model de exemplu pentru User
export interface User {
  id: number;
  username: string;
  password: string;
  email: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  role: 'user' | 'admin';
  createdAt: Date;
  updatedAt: Date;
  /** Secretul TOTP pentru 2FA (Google Authenticator) */
  totpSecret?: string;
  /** True dacă utilizatorul are 2FA activat */
  is2FAEnabled?: boolean;
}
