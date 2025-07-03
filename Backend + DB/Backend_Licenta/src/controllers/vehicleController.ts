import { Request, Response } from 'express';
import pool from '../config/database';
import { Vehicle } from '../models/vehicle';

export const getUserVehicles = async (req: Request, res: Response): Promise<void> => {
  try {
    const userId = parseInt(req.params.userId);
    const result = await pool.query(
      'SELECT * FROM vehicles WHERE user_id = $1',
      [userId]
    );
    res.json(result.rows);
  } catch (error) {
    console.error('Error fetching vehicles:', error);
    res.status(500).json({ message: 'Internal server error' });
  }
};

export const addVehicle = async (req: Request, res: Response): Promise<void> => {
  try {
    const userId = parseInt(req.params.userId);
    const { plateNumber, make, model, year, type } = req.body;

    // Validation
    if (!plateNumber || !make || !model || !year || !type) {
      res.status(400).json({ message: 'All vehicle details are required' });
      return;
    }

    // Validate plate number format (example: ABC-123 or ABC123)
    if (!plateNumber.match(/^[A-Z0-9]{1,8}(-[A-Z0-9]{1,4})?$/)) {
      res.status(400).json({ message: 'Invalid plate number format' });
      return;
    }

    // Validate year
    const currentYear = new Date().getFullYear();
    if (year < 1900 || year > currentYear + 1) {
      res.status(400).json({ message: 'Invalid vehicle year' });
      return;
    }

    // Validate vehicle type
    const validTypes = ['car', 'motorcycle', 'van'];
    if (!validTypes.includes(type)) {
      res.status(400).json({ message: 'Invalid vehicle type' });
      return;
    }

    // Check if plate number already exists
    const plateExists = await pool.query(
      'SELECT id FROM vehicles WHERE plate_number = $1',
      [plateNumber]
    );

    if (plateExists.rows.length > 0) {
      res.status(400).json({ message: 'Vehicle with this plate number already exists' });
      return;
    }

    const result = await pool.query(
      'INSERT INTO vehicles (user_id, plate_number, make, model, year, type) VALUES ($1, $2, $3, $4, $5, $6) RETURNING *',
      [userId, plateNumber, make, model, year, type]
    );

    res.status(201).json(result.rows[0]);
  } catch (error) {
    console.error('Error adding vehicle:', error);
    res.status(500).json({ message: 'Internal server error' });
  }
};

export const deleteVehicle = async (req: Request, res: Response): Promise<void> => {
  try {
    const vehicleId = parseInt(req.params.vehicleId);
    const userId = (req as any).user.id; // From JWT token

    // Verify vehicle belongs to user
    const vehicle = await pool.query(
      'SELECT * FROM vehicles WHERE id = $1 AND user_id = $2',
      [vehicleId, userId]
    );

    if (vehicle.rows.length === 0) {
      res.status(404).json({ message: 'Vehicle not found or unauthorized' });
      return;
    }

    // Check if vehicle has active reservations
    const activeReservations = await pool.query(
      `SELECT id FROM reservations 
       WHERE vehicle_id = $1 
       AND status IN ('pending', 'active')`,
      [vehicleId]
    );

    if (activeReservations.rows.length > 0) {
      res.status(400).json({ message: 'Cannot delete vehicle with active reservations' });
      return;
    }

    const result = await pool.query(
      'DELETE FROM vehicles WHERE id = $1 RETURNING *',
      [vehicleId]
    );

    res.json({ message: 'Vehicle deleted successfully' });
  } catch (error) {
    console.error('Error deleting vehicle:', error);
    res.status(500).json({ message: 'Internal server error' });
  }
};
