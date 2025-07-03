"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.getParkingSpots = void 0;
const database_1 = __importDefault(require("../config/database"));
const getParkingSpots = async (req, res) => {
    try {
        const { latitude, longitude, radius } = req.query;
        if (!latitude || !longitude || !radius) {
            res.status(400).json({ message: 'Latitude, longitude and radius are required' });
            return;
        }
        // Enhanced query with status details and better index usage
        const result = await database_1.default.query(`WITH current_reservations AS (
        SELECT 
          parking_spot_id,
          status as reservation_status,
          start_time,
          end_time
        FROM reservations 
        WHERE status IN ('pending', 'active')
          AND NOW() BETWEEN start_time AND end_time
      )
      SELECT 
        ps.*,
        COALESCE(cr.reservation_status, 'available') as current_status,
        cr.start_time as occupied_since,
        cr.end_time as occupied_until,
        point($1, $2) <@> point(ps.latitude, ps.longitude)::point as distance,
        CASE 
          WHEN cr.reservation_status IS NOT NULL THEN true
          ELSE false
        END as is_occupied
      FROM parking_spots ps
      LEFT JOIN current_reservations cr ON ps.id = cr.parking_spot_id
      WHERE point($1, $2) <@> point(ps.latitude, ps.longitude) <= $3
      ORDER BY distance`, [parseFloat(latitude), parseFloat(longitude), parseFloat(radius)]);
        // Fetch all active/pending reservations for these spots
        const spotIds = result.rows.map((row) => row.id);
        let reservationsMap = {};
        if (spotIds.length > 0) {
            const reservationsResult = await database_1.default.query(`SELECT parking_spot_id, start_time, end_time, status
         FROM reservations
         WHERE status IN ('pending', 'active')
           AND parking_spot_id = ANY($1::int[])`, [spotIds]);
            reservationsResult.rows.forEach((row) => {
                if (!reservationsMap[row.parking_spot_id]) {
                    reservationsMap[row.parking_spot_id] = [];
                }
                reservationsMap[row.parking_spot_id].push({
                    start: row.start_time,
                    end: row.end_time,
                    status: row.status
                });
            });
        }
        // Transform the response for better frontend consumption
        const spots = result.rows.map((row) => ({
            id: row.id,
            latitude: row.latitude,
            longitude: row.longitude,
            address: row.address,
            isOccupied: row.is_occupied,
            pricePerHour: row.price_per_hour,
            type: row.type,
            status: {
                current: row.current_status,
                occupiedSince: row.occupied_since,
                occupiedUntil: row.occupied_until
            },
            distance: parseFloat(row.distance).toFixed(2),
            occupiedIntervals: reservationsMap[row.id] || null
        }));
        // Add cache control headers for better performance
        res.set('Cache-Control', 'private, max-age=10'); // Cache for 10 seconds
        res.json({
            spots,
            meta: {
                timestamp: new Date().toISOString(),
                total: spots.length,
                radius: parseFloat(radius)
            }
        });
    }
    catch (error) {
        const err = error;
        console.error('Error fetching parking spots:', err);
        res.status(500).json({
            message: 'Internal server error',
            error: process.env.NODE_ENV === 'development' ? err.message : undefined
        });
    }
};
exports.getParkingSpots = getParkingSpots;
