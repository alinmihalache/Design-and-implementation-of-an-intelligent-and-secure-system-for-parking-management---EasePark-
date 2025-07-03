"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const express_1 = require("express");
const userController_1 = require("../controllers/userController");
const authController_1 = require("../controllers/authController");
const adminAuth_1 = require("../middleware/adminAuth");
const vehicleController_1 = require("../controllers/vehicleController");
const parkingSpotController_1 = require("../controllers/parkingSpotController");
const parkingSpotSSEController_1 = require("../controllers/parkingSpotSSEController");
const reservationController_1 = require("../controllers/reservationController");
const paymentController_1 = require("../controllers/paymentController");
const router = (0, express_1.Router)();
// Base route
router.get('/', (_req, res) => {
    res.send('Backend is running!');
});
// Auth routes
router.post('/api/auth/login', authController_1.authLimiter, authController_1.login);
router.post('/api/auth/register', authController_1.authLimiter, authController_1.register);
router.post('/api/auth/enable-2fa', authController_1.enable2FA);
router.post('/api/auth/verify-2fa', authController_1.verify2FA);
router.post('/api/auth/disable-2fa', authController_1.disable2FA);
// Protected routes
router.use(authController_1.verifyToken); // All routes below this will require authentication
// User routes
router.get('/api/users/me', userController_1.getMyProfile); // profilul utilizatorului autentificat
router.get('/api/users/:userId', userController_1.getUserProfile);
router.put('/api/users/:userId', userController_1.updateUserProfile);
router.get('/api/users/:userId/admin-status', userController_1.isAdmin);
// Vehicle routes
router.get('/api/users/:userId/vehicles', vehicleController_1.getUserVehicles);
router.post('/api/users/:userId/vehicles', vehicleController_1.addVehicle);
router.delete('/api/users/:userId/vehicles/:vehicleId', vehicleController_1.deleteVehicle);
// Parking spot routes
router.get('/api/parking-spots', parkingSpotController_1.getParkingSpots);
// Parking spot SSE route
router.get('/api/parking-spots/stream', parkingSpotSSEController_1.parkingSpotsSSE);
// Reservation routes
router.get('/api/users/:userId/reservations', reservationController_1.getUserReservations);
router.post('/api/reservations', reservationController_1.createReservation);
router.put('/api/reservations/:reservationId', reservationController_1.updateReservation);
router.delete('/api/reservations/:reservationId', reservationController_1.cancelReservation);
// Nou: DELETE cu userId în URL pentru compatibilitate Android
router.delete('/api/users/:userId/reservations/:reservationId', reservationController_1.cancelReservation);
router.put('/api/users/:userId/reservations/:reservationId', reservationController_1.updateReservation);
// Payment routes
router.post('/api/payments', paymentController_1.processPayment);
// Admin routes (necesită autentificare și rol de admin)
router.get('/api/admin/users', adminAuth_1.requireAdmin, userController_1.getUserProfile);
router.get('/api/admin/reservations', adminAuth_1.requireAdmin, reservationController_1.getUserReservations);
router.get('/api/admin/payments', adminAuth_1.requireAdmin, paymentController_1.processPayment);
router.put('/api/admin/parking-spots/:spotId', adminAuth_1.requireAdmin, parkingSpotController_1.getParkingSpots);
exports.default = router;
