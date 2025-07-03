import { Router } from 'express';
import { getUserProfile, updateUserProfile, isAdmin, getMyProfile } from '../controllers/userController';
import { login, register, verifyToken, authLimiter, enable2FA, verify2FA, disable2FA } from '../controllers/authController';
import { requireAdmin } from '../middleware/adminAuth';
import { getUserVehicles, addVehicle, deleteVehicle } from '../controllers/vehicleController';
import { getParkingSpots } from '../controllers/parkingSpotController';
import { parkingSpotsSSE } from '../controllers/parkingSpotSSEController';
import { getUserReservations, createReservation, updateReservation, cancelReservation } from '../controllers/reservationController';
import { processPayment } from '../controllers/paymentController';

const router = Router();

// Base route
router.get('/', (_req, res) => {
  res.send('Backend is running!');
});

// Auth routes
router.post('/api/auth/login', authLimiter, login);
router.post('/api/auth/register', authLimiter, register);
router.post('/api/auth/enable-2fa', enable2FA);
router.post('/api/auth/verify-2fa', verify2FA);
router.post('/api/auth/disable-2fa', disable2FA);

// Protected routes
router.use(verifyToken); // All routes below this will require authentication

// User routes
router.get('/api/users/me', getMyProfile); // profilul utilizatorului autentificat
router.get('/api/users/:userId', getUserProfile);
router.put('/api/users/:userId', updateUserProfile);
router.get('/api/users/:userId/admin-status', isAdmin);

// Vehicle routes
router.get('/api/users/:userId/vehicles', getUserVehicles);
router.post('/api/users/:userId/vehicles', addVehicle);
router.delete('/api/users/:userId/vehicles/:vehicleId', deleteVehicle);

// Parking spot routes
router.get('/api/parking-spots', getParkingSpots);
// Parking spot SSE route
router.get('/api/parking-spots/stream', parkingSpotsSSE);

// Reservation routes
router.get('/api/users/:userId/reservations', getUserReservations);
router.post('/api/reservations', createReservation);
router.put('/api/reservations/:reservationId', updateReservation);
router.delete('/api/reservations/:reservationId', cancelReservation);
// Nou: DELETE cu userId în URL pentru compatibilitate Android
router.delete('/api/users/:userId/reservations/:reservationId', cancelReservation);
router.put('/api/users/:userId/reservations/:reservationId', updateReservation);

// Payment routes
router.post('/api/payments', processPayment);

// Admin routes (necesită autentificare și rol de admin)
router.get('/api/admin/users', requireAdmin, getUserProfile);
router.get('/api/admin/reservations', requireAdmin, getUserReservations);
router.get('/api/admin/payments', requireAdmin, processPayment);
router.put('/api/admin/parking-spots/:spotId', requireAdmin, getParkingSpots);

export default router;
