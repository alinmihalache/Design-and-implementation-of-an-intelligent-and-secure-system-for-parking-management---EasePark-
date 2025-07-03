"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.notifyParkingSpotsUpdate = exports.parkingSpotsSSE = exports.broadcastParkingSpots = void 0;
const database_1 = __importDefault(require("../config/database"));
// Listă de clienți conectați la SSE
const clients = [];
// Trimite statusul actual al locurilor de parcare tuturor clienților conectați, inclusiv intervalele rezervărilor active/pending
const broadcastParkingSpots = async () => {
    try {
        // Obține toate locurile de parcare
        const spotsResult = await database_1.default.query('SELECT * FROM parking_spots');
        const spots = spotsResult.rows;
        // Obține toate rezervările active/pending grupate pe parking_spot_id
        const reservationsResult = await database_1.default.query(`SELECT parking_spot_id, start_time, end_time, status
       FROM reservations
       WHERE status IN ('pending', 'active')`);
        console.log('Rezervări active/pending:', reservationsResult.rows);
        // Grupează intervalele pe fiecare loc de parcare
        const reservationsMap = {};
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
        const now = new Date();
        // Construiește payload-ul pentru frontend
        const data = spots.map((spot) => {
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
    }
    catch (error) {
        console.error('Eroare la broadcast parking spots:', error);
    }
};
exports.broadcastParkingSpots = broadcastParkingSpots;
// Endpoint SSE
const parkingSpotsSSE = async (req, res) => {
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.flushHeaders();
    clients.push(res);
    // Trimite statusul inițial
    await (0, exports.broadcastParkingSpots)();
    // Elimină clientul la deconectare
    req.on('close', () => {
        const idx = clients.indexOf(res);
        if (idx !== -1)
            clients.splice(idx, 1);
    });
};
exports.parkingSpotsSSE = parkingSpotsSSE;
// Funcție de apelat după orice modificare la locuri de parcare (rezervare, eliberare etc)
exports.notifyParkingSpotsUpdate = exports.broadcastParkingSpots;
