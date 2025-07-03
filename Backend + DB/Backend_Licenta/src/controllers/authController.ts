import { Request, Response, NextFunction } from 'express';
import { LoginRequest } from '../types/auth';
import jwt from 'jsonwebtoken';
import pool from '../config/database';
import bcrypt from 'bcrypt';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import speakeasy from 'speakeasy';
import qrcode from 'qrcode';

// Rate limiter for authentication attempts
export const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5, // maximum 5 attempts
  message: { message: 'Too many login attempts. Please try again in 15 minutes.' },
  standardHeaders: true,
  legacyHeaders: false,
});

// Funcție pentru validarea complexității parolei
interface PasswordStrength {
  score: number;
  isValid: boolean;
  message: string;
  details: string[];
}

const checkPasswordStrength = (password: string): PasswordStrength => {
  let score = 0;
  const details: string[] = [];

  // Length check
  if (password.length < 8) {
    details.push('Password must be at least 8 characters long');
  } else {
    score += password.length >= 12 ? 2 : 1;
    if (password.length >= 12) details.push('Good length (12+ characters)');
  }

  // Uppercase letters
  if (!/[A-Z]/.test(password)) {
    details.push('Missing uppercase letter');
  } else {
    score += (password.match(/[A-Z]/g) || []).length > 1 ? 2 : 1;
    details.push('Contains uppercase letters');
  }

  // Lowercase letters
  if (!/[a-z]/.test(password)) {
    details.push('Missing lowercase letter');
  } else {
    score += (password.match(/[a-z]/g) || []).length > 1 ? 2 : 1;
    details.push('Contains lowercase letters');
  }

  // Numbers
  if (!/[0-9]/.test(password)) {
    details.push('Missing number');
  } else {
    score += (password.match(/[0-9]/g) || []).length > 1 ? 2 : 1;
    details.push('Contains numbers');
  }

  // Special characters
  if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
    details.push('Missing special character');
  } else {
    score += (password.match(/[!@#$%^&*(),.?":{}|<>]/g) || []).length > 1 ? 2 : 1;
    details.push('Contains special characters');
  }

  // Check for common patterns
  if (/^[a-zA-Z]+$/.test(password)) {
    score -= 2;
    details.push('Warning: Only letters');
  }
  if (/^[0-9]+$/.test(password)) {
    score -= 2;
    details.push('Warning: Only numbers');
  }
  if (/(.)\1{2,}/.test(password)) {
    score -= 2;
    details.push('Warning: Repeated characters');
  }

  // Calculate final score and message
  const isValid = score >= 5 && password.length >= 8;
  let message = '';
  
  if (score < 3) message = 'Very weak password';
  else if (score < 5) message = 'Weak password';
  else if (score < 8) message = 'Moderate password';
  else if (score < 10) message = 'Strong password';
  else message = 'Very strong password';

  return {
    score,
    isValid,
    message,
    details
  };
};

const validatePassword = (password: string): { isValid: boolean; message: string } => {
  const result = checkPasswordStrength(password);
  return { 
    isValid: result.isValid, 
    message: `${result.message}. ${result.isValid ? '' : result.details.join(', ') + '.'}`
  };
};

dotenv.config();

const JWT_SECRET = process.env.JWT_SECRET || 'fallback_secret';

if (JWT_SECRET === 'fallback_secret') {
  console.warn('WARNING: Using fallback JWT secret. Set JWT_SECRET in .env file!');
}

// Endpoint pentru activare 2FA (generează secret, salvează și returnează și QR code)
export const enable2FA = async (req: Request, res: Response): Promise<void> => {
  try {
    const { userId } = req.body;
    if (!userId) {
      res.status(400).json({ message: 'User ID is required' });
      return;
    }
    // Generează secret TOTP
    const secret = speakeasy.generateSecret({ name: 'ParkingApp' });
    // Salvează secretul și activează 2FA pentru user
    await pool.query(
      'UPDATE users SET totp_secret = $1, is_2fa_enabled = TRUE WHERE id = $2',
      [secret.base32, userId]
    );
    // Generează QR code (data URL)
    if (!secret.otpauth_url) {
      res.status(500).json({ message: 'Eroare la generarea otpauth_url pentru QR code' });
      return;
    }
    const qrDataUrl = await qrcode.toDataURL(secret.otpauth_url);
    // Trimite secretul ca text și QR code (frontend Android poate afișa QR code sau secretul)
    res.json({
      message: '2FA enabled. Add this secret to Google Authenticator.',
      secret: secret.base32,
      qr: qrDataUrl
    });
  } catch (error) {
    res.status(500).json({ message: 'Error enabling 2FA' });
  }
};

// Endpoint pentru verificare cod 2FA la login
export const verify2FA = async (req: Request, res: Response): Promise<void> => {
  try {
    const { userId, token } = req.body;
    if (!userId || !token) {
      res.status(400).json({ message: 'User ID și codul 2FA sunt necesare.' });
      return;
    }
    const userResult = await pool.query('SELECT * FROM users WHERE id = $1', [userId]);
    if (userResult.rows.length === 0) {
      res.status(404).json({ message: 'User not found' });
      return;
    }
    const user = userResult.rows[0];
    if (!user.totp_secret) {
      res.status(400).json({ message: '2FA nu este activat pentru acest user.' });
      return;
    }
    const verified = speakeasy.totp.verify({
      secret: user.totp_secret,
      encoding: 'base32',
      token,
      window: 1 // permite o mică abatere de timp
    });
    if (verified) {
      // Trimite userul actualizat
      res.json({
        message: '2FA code valid',
        user: {
          id: user.id,
          username: user.username,
          email: user.email,
          first_name: user.first_name,
          last_name: user.last_name,
          phone: user.phone,
          role: user.role,
          created_at: user.created_at,
          updated_at: user.updated_at,
          is_2fa_enabled: user.is_2fa_enabled
        }
      });
    } else {
      res.status(401).json({ message: 'Cod 2FA invalid' });
    }
  } catch (error) {
    res.status(500).json({ message: 'Eroare la verificarea codului 2FA' });
  }
};

// Modificare login: dacă userul are 2FA activat, cere codul 2FA
export const login = async (req: Request, res: Response): Promise<void> => {
  try {
    console.log('Received login request');
    const { username, email, password, token2FA } = req.body as any;
    const identifier = username || email;
    console.log('Checking credentials for:', identifier);
    if (!identifier || !password) {
      console.log('Missing username/email or password');
      res.status(400).json({ message: 'Username/email and password are required.' });
      return;
    }
    // Căutăm utilizatorul după username SAU email
    const result = await pool.query(
      'SELECT * FROM users WHERE username = $1 OR email = $1',
      [identifier]
    );
    console.log('Query result:', result.rows);
    if (result.rows.length > 0) {
      const user = result.rows[0];
      // Verificăm parola
      const validPassword = await bcrypt.compare(password, user.password);
      if (validPassword) {
        if (user.is_2fa_enabled) {
          // DEBUG: log secret și token primit pentru depanare
          console.log('TOTP secret:', user.totp_secret, 'Token primit:', token2FA);
          // Dacă userul are 2FA activat, verifică tokenul 2FA
          if (!token2FA) {
            res.status(403).json({ message: '2FA code required', userId: user.id });
            return;
          }
          const verified = speakeasy.totp.verify({
            secret: user.totp_secret,
            encoding: 'base32',
            token: token2FA,
            window: 1
          });
          if (!verified) {
            res.status(401).json({ message: 'Invalid 2FA code' });
            return;
          }
        }
        // Construim payload JWT cu toate datele relevante
        const token = jwt.sign(
          {
            id: user.id,
            username: user.username,
            email: user.email,
            firstName: user.first_name,
            lastName: user.last_name,
            phoneNumber: user.phone,
            role: user.role,
            createdAt: user.created_at,
            updatedAt: user.updated_at
          },
          JWT_SECRET,
          {
            expiresIn: '1h',
            issuer: 'parking-backend',
            audience: 'parking-app'
          }
        );
        console.log('Login successful for:', identifier);
        console.log('Trimitem user.is_2fa_enabled:', user.is_2fa_enabled, 'către frontend');
        res.json({
          message: 'Login successful',
          token,
          user: {
            id: user.id,
            username: user.username,
            email: user.email,
            first_name: user.first_name, // snake_case
            last_name: user.last_name,   // snake_case
            phone: user.phone,           // snake_case
            role: user.role,
            created_at: user.created_at,
            updated_at: user.updated_at,
            is_2fa_enabled: user.is_2fa_enabled // ADĂUGAT pentru frontend
          }
        });
      } else {
        res.status(401).json({ message: 'Incorrect password' });
      }
    } else {
      res.status(401).json({ message: 'User not found' });
    }
  } catch (error) {
    console.error('Eroare la autentificare:', error);
    res.status(500).json({ message: 'Eroare internă la server' });
  }
};

export const verifyToken = (req: Request, res: Response, next: NextFunction): void => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  
  if (!token) {    res.status(401).json({ message: 'Token missing' });
    return;
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      res.status(403).json({ message: 'Invalid token' });
      return;
    }
    (req as any).user = user;
    next();
  });
};

export const register = async (req: Request, res: Response): Promise<void> => {
  try {
    const { username, password, email, phone, adminCode } = req.body;
    // Validation
    if (!username || !password || !email || !phone) {
      res.status(400).json({ message: 'Username, password, email and phone are required.' });
      return;
    }
    // Restricție: maxim 2 conturi per număr de telefon
    const phoneCountResult = await pool.query(
      'SELECT COUNT(*) FROM users WHERE phone = $1',
      [phone]
    );
    if (parseInt(phoneCountResult.rows[0].count) >= 2) {
      res.status(400).json({ message: 'You cannot have more than 2 accounts with the same phone number.' });
      return;
    }
    // Validare complexitate parolă
    const passwordValidation = validatePassword(password);
    if (!passwordValidation.isValid) {
      res.status(400).json({ message: passwordValidation.message });
      return;
    }
    // Verificăm dacă username-ul există deja
    const userExists = await pool.query(
      'SELECT * FROM users WHERE username = $1',
      [username]
    );
    if (userExists.rows.length > 0) {
      res.status(400).json({ message: 'Username already exists.' });
      return;
    }
    // Hash password
    const saltRounds = 10;
    const hashedPassword = await bcrypt.hash(password, saltRounds);
    // Inserăm noul utilizator
    const insertResult = await pool.query(
      'INSERT INTO users (username, password, email, phone) VALUES ($1, $2, $3, $4) RETURNING id',
      [username, hashedPassword, email, phone]
    );
    const newUserId = insertResult.rows[0].id;
    // Selectăm user-ul complet pentru JWT și răspuns
    const userResult = await pool.query(
      'SELECT * FROM users WHERE id = $1',
      [newUserId]
    );
    const user = userResult.rows[0];
    const token = jwt.sign(
      {
        id: user.id,
        username: user.username,
        email: user.email,
        firstName: user.first_name,
        lastName: user.last_name,
        phoneNumber: user.phone,
        role: user.role,
        createdAt: user.created_at,
        updatedAt: user.updated_at
      },
      JWT_SECRET,
      { 
        expiresIn: '1h',
        issuer: 'parking-backend',
        audience: 'parking-app'
      }
    );
    res.status(201).json({
      message: 'Account created successfully!',
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        firstName: user.first_name,
        lastName: user.last_name,
        phoneNumber: user.phone,
        role: user.role,
        createdAt: user.created_at,
        updatedAt: user.updated_at
      },
      token
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ message: 'Internal server error' });
  }
};

// Endpoint pentru dezactivare 2FA
export const disable2FA = async (req: Request, res: Response): Promise<void> => {
  try {
    const { userId } = req.body;
    if (!userId) {
      res.status(400).json({ message: 'User ID is required' });
      return;
    }
    await pool.query(
      'UPDATE users SET totp_secret = NULL, is_2fa_enabled = FALSE WHERE id = $1',
      [userId]
    );
    res.json({ message: '2FA has been disabled.' });
  } catch (error) {
    res.status(500).json({ message: 'Error disabling 2FA' });
  }
};
