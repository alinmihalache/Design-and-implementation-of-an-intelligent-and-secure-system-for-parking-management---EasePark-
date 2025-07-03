"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.processPayment = void 0;
const database_1 = __importDefault(require("../config/database"));
const processPayment = async (req, res) => {
    try {
        const { reservationId, userId, amount, paymentMethod } = req.body;
        // Validation
        if (!reservationId || !userId || !amount || !paymentMethod) {
            res.status(400).json({ message: 'All payment details are required' });
            return;
        }
        // Verify reservation exists and belongs to user
        const reservation = await database_1.default.query('SELECT * FROM reservations WHERE id = $1 AND user_id = $2', [reservationId, userId]);
        if (reservation.rows.length === 0) {
            res.status(404).json({ message: 'Reservation not found' });
            return;
        }
        // Process payment (in real application, integrate with payment gateway)
        const result = await database_1.default.query(`INSERT INTO payments 
       (reservation_id, user_id, amount, status, payment_method, timestamp)
       VALUES ($1, $2, $3, 'completed', $4, NOW())
       RETURNING *`, [reservationId, userId, amount, paymentMethod]);
        // Update reservation status
        await database_1.default.query('UPDATE reservations SET status = $1 WHERE id = $2', ['active', reservationId]);
        res.status(201).json({
            message: 'Payment processed successfully',
            payment: result.rows[0]
        });
    }
    catch (error) {
        console.error('Error processing payment:', error);
        res.status(500).json({ message: 'Internal server error' });
    }
};
exports.processPayment = processPayment;
