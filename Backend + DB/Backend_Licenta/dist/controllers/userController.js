"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.isAdmin = exports.updateUserProfile = exports.getMyProfile = exports.getUserProfile = void 0;
const database_1 = __importDefault(require("../config/database"));
// Get user profile
const getUserProfile = async (req, res) => {
    try {
        const userId = parseInt(req.params.userId);
        const result = await database_1.default.query('SELECT id, username, email, first_name, last_name, phone, role, created_at, updated_at FROM users WHERE id = $1', [userId]);
        if (result.rows.length === 0) {
            res.status(404).json({ message: 'User not found' });
            return;
        }
        res.json(result.rows[0]);
    }
    catch (error) {
        console.error('Error fetching user profile:', error);
        res.status(500).json({ message: 'Internal server error' });
    }
};
exports.getUserProfile = getUserProfile;
// Get current user's profile (/api/users/me)
const getMyProfile = async (req, res) => {
    try {
        const userId = req.user?.id;
        if (!userId || isNaN(Number(userId))) {
            res.status(400).json({ message: 'Invalid user id' });
            return;
        }
        const result = await database_1.default.query('SELECT id, username, email, first_name, last_name, phone, role, created_at, updated_at FROM users WHERE id = $1', [userId]);
        if (result.rows.length === 0) {
            res.status(404).json({ message: 'User not found' });
            return;
        }
        res.json(result.rows[0]);
    }
    catch (error) {
        console.error('Error fetching user profile (me):', error);
        res.status(500).json({ message: 'Internal server error' });
    }
};
exports.getMyProfile = getMyProfile;
// Update user profile
const updateUserProfile = async (req, res) => {
    try {
        const userId = parseInt(req.params.userId);
        const { email, firstName, lastName, phone } = req.body;
        // Validare email
        if (email && !email.match(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)) {
            res.status(400).json({ message: 'Invalid email format' });
            return;
        }
        // Validare număr de telefon (format internațional)
        if (phone && !phone.match(/^\+?[1-9]\d{1,14}$/)) {
            res.status(400).json({ message: 'Invalid phone number format' });
            return;
        }
        // Verificare dacă emailul există deja pentru alt utilizator
        if (email) {
            const emailExists = await database_1.default.query('SELECT id FROM users WHERE email = $1 AND id != $2', [email, userId]);
            if (emailExists.rows.length > 0) {
                res.status(400).json({ message: 'Email already in use' });
                return;
            }
        }
        const result = await database_1.default.query(`UPDATE users 
       SET email = COALESCE($1, email),
           first_name = COALESCE($2, first_name),
           last_name = COALESCE($3, last_name),
           phone = COALESCE($4, phone),
           updated_at = CURRENT_TIMESTAMP
       WHERE id = $5
       RETURNING id, username, email, first_name, last_name, phone, role, created_at, updated_at`, [email, firstName, lastName, phone, userId]);
        if (result.rows.length === 0) {
            res.status(404).json({ message: 'User not found' });
            return;
        }
        res.json(result.rows[0]);
    }
    catch (error) {
        console.error('Error updating user profile:', error);
        res.status(500).json({ message: 'Internal server error' });
    }
};
exports.updateUserProfile = updateUserProfile;
// Check if user is admin
const isAdmin = async (req, res) => {
    try {
        const userId = parseInt(req.params.userId);
        const result = await database_1.default.query('SELECT role FROM users WHERE id = $1', [userId]);
        if (result.rows.length === 0) {
            res.status(404).json({ message: 'User not found' });
            return;
        }
        res.json({
            isAdmin: result.rows[0].role === 'admin'
        });
    }
    catch (error) {
        console.error('Error checking admin status:', error);
        res.status(500).json({ message: 'Internal server error' });
    }
};
exports.isAdmin = isAdmin;
