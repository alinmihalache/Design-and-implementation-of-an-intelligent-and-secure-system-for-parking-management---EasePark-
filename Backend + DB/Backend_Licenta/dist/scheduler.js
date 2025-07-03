"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.startScheduler = startScheduler;
const node_cron_1 = __importDefault(require("node-cron"));
const database_1 = __importDefault(require("./config/database"));
const parkingSpotSSEController_1 = require("./controllers/parkingSpotSSEController");
// Activează rezervările care au început
const activateReservationsJob = node_cron_1.default.schedule('* * * * *', async () => {
    try {
        // Log ora curentă pe server (UTC)
        const nowUtc = new Date().toISOString();
        console.log(`[DEBUG] Ora curentă pe server (UTC): ${nowUtc}`);
        console.log('[Scheduler] Verific activarea rezervărilor...');
        // Log pending reservations and current UTC time for debug
        const pending = await database_1.default.query(`SELECT id, parking_spot_id, start_time, status FROM reservations WHERE status = 'pending' ORDER BY start_time ASC`);
        console.log('[Scheduler][DEBUG] Rezervări pending:', JSON.stringify(pending.rows, null, 2));
        // console.log('[Scheduler][DEBUG] NOW() UTC:', now.rows[0].now_utc);
        const query = `
      UPDATE reservations
      SET status = 'active'
      WHERE status = 'pending' AND start_time <= $1
      RETURNING id, parking_spot_id;
    `;
        const result = await database_1.default.query(query, [nowUtc]);
        if (result.rows.length > 0) {
            console.log(`[Scheduler] Activat ${result.rows.length} rezervări.`);
            for (const reservation of result.rows) {
                await database_1.default.query('UPDATE parking_spots SET is_occupied = TRUE WHERE id = $1', [reservation.parking_spot_id]);
            }
            console.log('[Scheduler] Se pregătește trimiterea notificării SSE pentru activare...');
            await (0, parkingSpotSSEController_1.notifyParkingSpotsUpdate)();
        }
    }
    catch (err) {
        console.error('[Scheduler] Eroare la activare:', err);
    }
});
// Marchează rezervările terminate ca 'completed'
const completeReservationsJob = node_cron_1.default.schedule('* * * * *', async () => {
    try {
        const nowUtc = new Date().toISOString();
        console.log(`[DEBUG] Ora curentă pe server (UTC) pentru finalizare: ${nowUtc}`);
        console.log('[Scheduler] Verific finalizarea rezervărilor...');
        const active = await database_1.default.query(`SELECT id, parking_spot_id, end_time, status FROM reservations WHERE status = 'active' ORDER BY end_time ASC`);
        console.log('[Scheduler][DEBUG] Rezervări active:', JSON.stringify(active.rows, null, 2));
        const query = `
      UPDATE reservations
      SET status = 'completed'
      WHERE status = 'active' AND end_time <= $1
      RETURNING id, parking_spot_id;
    `;
        const result = await database_1.default.query(query, [nowUtc]);
        if (result.rows.length > 0) {
            console.log(`[Scheduler] Finalizat ${result.rows.length} rezervări.`);
            for (const reservation of result.rows) {
                await database_1.default.query('UPDATE parking_spots SET is_occupied = FALSE WHERE id = $1', [reservation.parking_spot_id]);
            }
            console.log('[Scheduler] Se pregătește trimiterea notificării SSE pentru finalizare...');
            await (0, parkingSpotSSEController_1.notifyParkingSpotsUpdate)();
        }
    }
    catch (err) {
        console.error('[Scheduler] Eroare la finalizare:', err);
    }
});
function startScheduler() {
    console.log('[Scheduler] Pornit!');
    activateReservationsJob.start();
    completeReservationsJob.start();
}
