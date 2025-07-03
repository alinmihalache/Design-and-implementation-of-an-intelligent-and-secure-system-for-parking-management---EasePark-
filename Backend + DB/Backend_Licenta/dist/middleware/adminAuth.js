"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireAdmin = void 0;
const requireAdmin = (req, res, next) => {
    // Verificăm dacă utilizatorul este autentificat și are rol de admin
    const user = req.user;
    if (!user) {
        res.status(401).json({ message: 'Authentication required' });
        return;
    }
    if (user.role !== 'admin') {
        res.status(403).json({ message: 'Admin access required' });
        return;
    }
    next();
};
exports.requireAdmin = requireAdmin;
