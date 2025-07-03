import { Request, Response } from 'express';
import pool from '../config/database';

// Listă de clienți conectați la SSE
const clients: Response[] = [];

// Trimite statusul actual al locurilor de parcare tuturor clienților conectați, inclusiv intervalele rezervărilor active/pending
export const broadcastParkingSpots = async () => {
  try {
    // Obține toate locurile de parcare
    const spotsResult = await pool.query('SELECT * FROM parking_spots');
    const spots = spotsResult.rows;

    // Obține toate rezervările active/pending grupate pe parking_spot_id
    const reservationsResult = await pool.query(
      `SELECT parking_spot_id, start_time, end_time, status
       FROM reservations
       WHERE status IN ('pending', 'active')`
    );
    console.log('Rezervări active/pending:', reservationsResult.rows);
    // Grupează intervalele pe fiecare loc de parcare
    const reservationsMap: { [spotId: number]: { start: string, end: string, status: string }[] } = {};
    reservationsResult.rows.forEach((row: any) => {
      if (!reservationsMap[row.parking_spot_id]) {
        reservationsMap[row.parking_spot_id] = [];
      }
      reservationsMap[row.parking_spot_id].push({
        start: row.start_time,
        end: row.end_time,
        status: row.status
      });
    });

    const now = new Date();

    // Construiește payload-ul pentru frontend
    const data = spots.map((spot: any) => {
      const intervals = reservationsMap[spot.id] || [];
      // Verifică dacă există vreun interval activ/pending valabil ACUM
      const isOccupied = intervals.some(interval => {
        const start = new Date(interval.start);
        const end = new Date(interval.end);
        return start <= now && now <= end;
      });
      return {
        id: spot.id,
        latitude: spot.latitude,
        longitude: spot.longitude,
        address: spot.address,
        isOccupied,
        pricePerHour: spot.price_per_hour,
        type: spot.type,
        occupiedIntervals: intervals
      };
    });

    console.log('Payload SSE trimis:', JSON.stringify(data, null, 2));
    const json = JSON.stringify(data);
    clients.forEach(res => {
      res.write(`data: ${json}\n\n`);
    });
  } catch (error) {
    console.error('Eroare la broadcast parking spots:', error);
  }
};

// Endpoint SSE
export const parkingSpotsSSE = async (req: Request, res: Response) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();
  clients.push(res);

  // Trimite statusul inițial
  await broadcastParkingSpots();

  // Elimină clientul la deconectare
  req.on('close', () => {
    const idx = clients.indexOf(res);
    if (idx !== -1) clients.splice(idx, 1);
  });
};

// Funcție de apelat după orice modificare la locuri de parcare (rezervare, eliberare etc)
export const notifyParkingSpotsUpdate = broadcastParkingSpots;
