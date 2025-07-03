import { Request, Response } from 'express';
import pool from '../config/database';
import { Reservation } from '../models/reservation';
import { notifyParkingSpotsUpdate } from './parkingSpotSSEController';

export const getUserReservations = async (req: Request, res: Response): Promise<void> => {
  try {
    const userId = parseInt(req.params.userId);
    const result = await pool.query(
      `SELECT r.*, p.address, p.price_per_hour, v.plate_number 
       FROM reservations r
       JOIN parking_spots p ON r.parking_spot_id = p.id
       JOIN vehicles v ON r.vehicle_id = v.id
       WHERE r.user_id = $1
       ORDER BY r.start_time DESC`,
      [userId]
    );
    res.json(result.rows);
  } catch (error) {
    console.error('Error fetching reservations:', error);
    res.status(500).json({ message: 'Internal server error' });
  }
};

export const createReservation = async (req: Request, res: Response): Promise<void> => {
  const client = await pool.connect();
  
  try {
    console.log('Cerere rezervare primită:', req.body);
    const { userId, vehicleId, parkingSpotId, startTime, endTime } = req.body;

    // Input validation
    if (!userId || !vehicleId || !parkingSpotId || !startTime || !endTime) {
      res.status(400).json({ message: 'All reservation details are required' });
      return;
    }

    // Validate times (folosim Date doar pentru validare, nu pentru inserare)
    const parsedStartTime = new Date(startTime);
    const parsedEndTime = new Date(endTime);
    if (parsedStartTime >= parsedEndTime || parsedStartTime < new Date()) {
      res.status(400).json({ message: 'Invalid time range' });
      return;
    }

    console.log('Tipuri și valori primite:');
    console.log('userId:', userId, typeof userId);
    console.log('vehicleId:', vehicleId, typeof vehicleId);
    console.log('parkingSpotId:', parkingSpotId, typeof parkingSpotId);
    console.log('startTime:', startTime, typeof startTime);
    console.log('endTime:', endTime, typeof endTime);
    console.log('parsedStartTime:', parsedStartTime.toISOString(), parsedStartTime instanceof Date);
    console.log('parsedEndTime:', parsedEndTime.toISOString(), parsedEndTime instanceof Date);

    await client.query('BEGIN');

    // Check if parking spot exists and get its details
    const spotDetails = await client.query(
      'SELECT id, price_per_hour FROM parking_spots WHERE id = $1 FOR UPDATE',
      [parkingSpotId]
    );

    if (spotDetails.rows.length === 0) {
      await client.query('ROLLBACK');
      res.status(404).json({ message: 'Parking spot not found' });
      return;
    }

    // Check for overlapping reservations with FOR UPDATE to prevent race conditions
    const overlappingReservations = await client.query(
      `SELECT id, start_time, end_time FROM reservations 
       WHERE parking_spot_id = $1 
       AND status IN ('active', 'pending')
       AND $2 < end_time 
       AND $3 > start_time
       FOR UPDATE`,
      [parkingSpotId, startTime, endTime]
    );
    console.log('Rezervări suprapuse găsite:', overlappingReservations.rows);

    if (overlappingReservations.rows.length > 0) {
      await client.query('ROLLBACK');
      res.status(409).json({ 
        message: 'Parking spot is not available for selected time period',
        code: 'SPOT_UNAVAILABLE'
      });
      return;
    }

    // Calculate total price
    const hours = (parsedEndTime.getTime() - parsedStartTime.getTime()) / (1000 * 60 * 60);
    const totalPrice = hours * spotDetails.rows[0].price_per_hour;

    // Loghează obiectul ce va fi inserat în DB
    const reservationDataToSave = {
      userId,
      vehicleId,
      parkingSpotId,
      startTime: parsedStartTime.toISOString(),
      endTime: parsedEndTime.toISOString(),
      totalPrice
    };
    console.log('Obiectul ce va fi inserat în DB:', reservationDataToSave);

    // Create the reservation
    const result = await client.query(
      `INSERT INTO reservations 
       (user_id, vehicle_id, parking_spot_id, start_time, end_time, status, total_price)
       VALUES ($1, $2, $3, $4, $5, 'pending', $6)
       RETURNING id, user_id, vehicle_id, parking_spot_id, start_time, end_time, status, total_price`,
      [userId, vehicleId, parkingSpotId, startTime, endTime, totalPrice]
    );
    console.log('Rezervare salvată:', result.rows[0]);

    // După crearea unei rezervări cu succes, notificăm SSE
    await client.query('COMMIT');
    await notifyParkingSpotsUpdate();

    res.status(201).json({
      ...result.rows[0],
      message: 'Reservation created successfully'
    });

  } catch (error) {
    await client.query('ROLLBACK');
    const err = error as Error;
    console.error('Error creating reservation:', err);
    res.status(500).json({ 
      message: 'Internal server error',
      error: process.env.NODE_ENV === 'development' ? err.message : undefined
    });
  } finally {
    client.release();
  }
};

export const updateReservation = async (req: Request, res: Response): Promise<void> => {
  try {
    const reservationId = parseInt(req.params.reservationId);
    const { startTime, endTime, status } = req.body;

    // Obține rezervarea și detaliile locului de parcare
    const reservationResult = await pool.query(
      `SELECT * FROM reservations WHERE id = $1`,
      [reservationId]
    );
    if (reservationResult.rows.length === 0) {
      res.status(404).json({ message: 'Reservation not found' });
      return;
    }
    const reservation = reservationResult.rows[0];
    let newStart = startTime || reservation.start_time;
    let newEnd = endTime || reservation.end_time;

    // Dacă se modifică perioada, recalculează prețul total
    let totalPrice = reservation.total_price;
    if (startTime || endTime) {
      // Obține prețul pe oră al locului de parcare
      const spotResult = await pool.query(
        `SELECT price_per_hour FROM parking_spots WHERE id = $1`,
        [reservation.parking_spot_id]
      );
      const pricePerHour = spotResult.rows[0]?.price_per_hour || 0;
      const parsedStart = new Date(newStart);
      const parsedEnd = new Date(newEnd);
      const hours = (parsedEnd.getTime() - parsedStart.getTime()) / (1000 * 60 * 60);
      totalPrice = hours * pricePerHour;
    }

    const result = await pool.query(
      `UPDATE reservations 
       SET start_time = COALESCE($1, start_time),
           end_time = COALESCE($2, end_time),
           status = COALESCE($3, status),
           total_price = $4
       WHERE id = $5
       RETURNING *`,
      [startTime, endTime, status, totalPrice, reservationId]
    );

    if (result.rows.length === 0) {
      res.status(404).json({ message: 'Reservation not found' });
      return;
    }

    // Notifică SSE după update
    await notifyParkingSpotsUpdate();
    res.json(result.rows[0]);
  } catch (error) {
    console.error('Error updating reservation:', error);
    res.status(500).json({ message: 'Internal server error' });
  }
};

export const cancelReservation = async (req: Request, res: Response): Promise<void> => {
  try {
    const reservationId = parseInt(req.params.reservationId);
    
    const result = await pool.query(
      `UPDATE reservations 
       SET status = 'cancelled'
       WHERE id = $1
       RETURNING *`,
      [reservationId]
    );

    // După anulare rezervare, notificăm SSE
    if (result.rows.length === 0) {
      res.status(404).json({ message: 'Reservation not found' });
      return;
    }
    await notifyParkingSpotsUpdate();
    res.json({ message: 'Reservation cancelled successfully' });
  } catch (error) {
    console.error('Error cancelling reservation:', error);
    res.status(500).json({ message: 'Internal server error' });
  }
};
