"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.disable2FA = exports.register = exports.verifyToken = exports.login = exports.verify2FA = exports.enable2FA = exports.authLimiter = void 0;
const jsonwebtoken_1 = __importDefault(require("jsonwebtoken"));
const database_1 = __importDefault(require("../config/database"));
const bcrypt_1 = __importDefault(require("bcrypt"));
const express_rate_limit_1 = __importDefault(require("express-rate-limit"));
const dotenv_1 = __importDefault(require("dotenv"));
const speakeasy_1 = __importDefault(require("speakeasy"));
const qrcode_1 = __importDefault(require("qrcode"));
// Rate limiter for authentication attempts
exports.authLimiter = (0, express_rate_limit_1.default)({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 5, // maximum 5 attempts
    message: { message: 'Too many login attempts. Please try again in 15 minutes.' },
    standardHeaders: true,
    legacyHeaders: false,
});
const checkPasswordStrength = (password) => {
    let score = 0;
    const details = [];
    // Length check
    if (password.length < 8) {
        details.push('Password must be at least 8 characters long');
    }
    else {
        score += password.length >= 12 ? 2 : 1;
        if (password.length >= 12)
            details.push('Good length (12+ characters)');
    }
    // Uppercase letters
    if (!/[A-Z]/.test(password)) {
        details.push('Missing uppercase letter');
    }
    else {
        score += (password.match(/[A-Z]/g) || []).length > 1 ? 2 : 1;
        details.push('Contains uppercase letters');
    }
    // Lowercase letters
    if (!/[a-z]/.test(password)) {
        details.push('Missing lowercase letter');
    }
    else {
        score += (password.match(/[a-z]/g) || []).length > 1 ? 2 : 1;
        details.push('Contains lowercase letters');
    }
    // Numbers
    if (!/[0-9]/.test(password)) {
        details.push('Missing number');
    }
    else {
        score += (password.match(/[0-9]/g) || []).length > 1 ? 2 : 1;
        details.push('Contains numbers');
    }
    // Special characters
    if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
        details.push('Missing special character');
    }
    else {
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
    if (score < 3)
        message = 'Very weak password';
    else if (score < 5)
        message = 'Weak password';
    else if (score < 8)
        message = 'Moderate password';
    else if (score < 10)
        message = 'Strong password';
    else
        message = 'Very strong password';
    return {
        score,
        isValid,
        message,
        details
    };
};
const validatePassword = (password) => {
    const result = checkPasswordStrength(password);
    return {
        isValid: result.isValid,
        message: `${result.message}. ${result.isValid ? '' : result.details.join(', ') + '.'}`
    };
};
dotenv_1.default.config();
const JWT_SECRET = process.env.JWT_SECRET || 'fallback_secret';
if (JWT_SECRET === 'fallback_secret') {
    console.warn('WARNING: Using fallback JWT secret. Set JWT_SECRET in .env file!');
}
// Endpoint pentru activare 2FA (generează secret, salvează și returnează și QR code)
const enable2FA = async (req, res) => {
    try {
        const { userId } = req.body;
        if (!userId) {
            res.status(400).json({ message: 'User ID is required' });
            return;
        }
        // Generează secret TOTP
        const secret = speakeasy_1.default.generateSecret({ name: 'ParkingApp' });
        // Salvează secretul și activează 2FA pentru user
        await database_1.default.query('UPDATE users SET totp_secret = $1, is_2fa_enabled = TRUE WHERE id = $2', [secret.base32, userId]);
        // Generează QR code (data URL)
        if (!secret.otpauth_url) {
            res.status(500).json({ message: 'Eroare la generarea otpauth_url pentru QR code' });
            return;
        }
        const qrDataUrl = await qrcode_1.default.toDataURL(secret.otpauth_url);
        // Trimite secretul ca text și QR code (frontend Android poate afișa QR code sau secretul)
        res.json({
            message: '2FA enabled. Add this secret to Google Authenticator.',
            secret: secret.base32,
            qr: qrDataUrl
        });
    }
    catch (error) {
        res.status(500).json({ message: 'Error enabling 2FA' });
    }
};
exports.enable2FA = enable2FA;
// Endpoint pentru verificare cod 2FA la login
const verify2FA = async (req, res) => {
    try {
        const { userId, token } = req.body;
        if (!userId || !token) {
            res.status(400).json({ message: 'User ID și codul 2FA sunt necesare.' });
            return;
        }
        const userResult = await database_1.default.query('SELECT * FROM users WHERE id = $1', [userId]);
        if (userResult.rows.length === 0) {
            res.status(404).json({ message: 'User not found' });
            return;
        }
        const user = userResult.rows[0];
        if (!user.totp_secret) {
            res.status(400).json({ message: '2FA nu este activat pentru acest user.' });
            return;
        }
        const verified = speakeasy_1.default.totp.verify({
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
        }
        else {
            res.status(401).json({ message: 'Cod 2FA invalid' });
        }
    }
    catch (error) {
        res.status(500).json({ message: 'Eroare la verificarea codului 2FA' });
    }
};
exports.verify2FA = verify2FA;
// Modificare login: dacă userul are 2FA activat, cere codul 2FA
const login = async (req, res) => {
    try {
        console.log('Received login request');
        const { username, email, password, token2FA } = req.body;
        const identifier = username || email;
        console.log('Checking credentials for:', identifier);
        if (!identifier || !password) {
            console.log('Missing username/email or password');
            res.status(400).json({ message: 'Username/email and password are required.' });
            return;
        }
        // Căutăm utilizatorul după username SAU email
        const result = await database_1.default.query('SELECT * FROM users WHERE username = $1 OR email = $1', [identifier]);
        console.log('Query result:', result.rows);
        if (result.rows.length > 0) {
            const user = result.rows[0];
            // Verificăm parola
            const validPassword = await bcrypt_1.default.compare(password, user.password);
            if (validPassword) {
                if (user.is_2fa_enabled) {
                    // DEBUG: log secret și token primit pentru depanare
                    console.log('TOTP secret:', user.totp_secret, 'Token primit:', token2FA);
                    // Dacă userul are 2FA activat, verifică tokenul 2FA
                    if (!token2FA) {
                        res.status(403).json({ message: '2FA code required', userId: user.id });
                        return;
                    }
                    const verified = speakeasy_1.default.totp.verify({
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
                const token = jsonwebtoken_1.default.sign({
                    id: user.id,
                    username: user.username,
                    email: user.email,
                    firstName: user.first_name,
                    lastName: user.last_name,
                    phoneNumber: user.phone,
                    role: user.role,
                    createdAt: user.created_at,
                    updatedAt: user.updated_at
                }, JWT_SECRET, {
                    expiresIn: '1h',
                    issuer: 'parking-backend',
                    audience: 'parking-app'
                });
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
                        last_name: user.last_name, // snake_case
                        phone: user.phone, // snake_case
                        role: user.role,
                        created_at: user.created_at,
                        updated_at: user.updated_at,
                        is_2fa_enabled: user.is_2fa_enabled // ADĂUGAT pentru frontend
                    }
                });
            }
            else {
                res.status(401).json({ message: 'Incorrect password' });
            }
        }
        else {
            res.status(401).json({ message: 'User not found' });
        }
    }
    catch (error) {
        console.error('Eroare la autentificare:', error);
        res.status(500).json({ message: 'Eroare internă la server' });
    }
};
exports.login = login;
const verifyToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    if (!token) {
        res.status(401).json({ message: 'Token missing' });
        return;
    }
    jsonwebtoken_1.default.verify(token, JWT_SECRET, (err, user) => {
        if (err) {
            res.status(403).json({ message: 'Invalid token' });
            return;
        }
        req.user = user;
        next();
    });
};
exports.verifyToken = verifyToken;
const register = async (req, res) => {
    try {
        const { username, password, email, phone, adminCode } = req.body;
        // Validation
        if (!username || !password || !email || !phone) {
            res.status(400).json({ message: 'Username, password, email and phone are required.' });
            return;
        }
        // Restricție: maxim 2 conturi per număr de telefon
        const phoneCountResult = await database_1.default.query('SELECT COUNT(*) FROM users WHERE phone = $1', [phone]);
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
        const userExists = await database_1.default.query('SELECT * FROM users WHERE username = $1', [username]);
        if (userExists.rows.length > 0) {
            res.status(400).json({ message: 'Username already exists.' });
            return;
        }
        // Hash password
        const saltRounds = 10;
        const hashedPassword = await bcrypt_1.default.hash(password, saltRounds);
        // Inserăm noul utilizator
        const insertResult = await database_1.default.query('INSERT INTO users (username, password, email, phone) VALUES ($1, $2, $3, $4) RETURNING id', [username, hashedPassword, email, phone]);
        const newUserId = insertResult.rows[0].id;
        // Selectăm user-ul complet pentru JWT și răspuns
        const userResult = await database_1.default.query('SELECT * FROM users WHERE id = $1', [newUserId]);
        const user = userResult.rows[0];
        const token = jsonwebtoken_1.default.sign({
            id: user.id,
            username: user.username,
            email: user.email,
            firstName: user.first_name,
            lastName: user.last_name,
            phoneNumber: user.phone,
            role: user.role,
            createdAt: user.created_at,
            updatedAt: user.updated_at
        }, JWT_SECRET, {
            expiresIn: '1h',
            issuer: 'parking-backend',
            audience: 'parking-app'
        });
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
    }
    catch (error) {
        console.error('Registration error:', error);
        res.status(500).json({ message: 'Internal server error' });
    }
};
exports.register = register;
// Endpoint pentru dezactivare 2FA
const disable2FA = async (req, res) => {
    try {
        const { userId } = req.body;
        if (!userId) {
            res.status(400).json({ message: 'User ID is required' });
            return;
        }
        await database_1.default.query('UPDATE users SET totp_secret = NULL, is_2fa_enabled = FALSE WHERE id = $1', [userId]);
        res.json({ message: '2FA has been disabled.' });
    }
    catch (error) {
        res.status(500).json({ message: 'Error disabling 2FA' });
    }
};
exports.disable2FA = disable2FA;
