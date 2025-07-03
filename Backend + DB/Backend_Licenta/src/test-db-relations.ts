import pool from './config/database';

async function testRelations() {
  try {
    console.log('--- USERS ---');
    const users = await pool.query('SELECT id, username, email, role FROM users');
    console.table(users.rows);

    console.log('\n--- VEHICLES ---');
    const vehicles = await pool.query('SELECT id, user_id, plate_number, make, model, year, type FROM vehicles');
    console.table(vehicles.rows);

    console.log('\n--- PARKING SPOTS ---');
    const spots = await pool.query('SELECT id, latitude, longitude, address, price_per_hour, type FROM parking_spots');
    console.table(spots.rows);

    console.log('\n--- RESERVATIONS (with user & vehicle) ---');
    const reservations = await pool.query(`
      SELECT r.id, r.user_id, u.username, r.vehicle_id, v.plate_number, r.parking_spot_id, r.status, r.start_time, r.end_time
      FROM reservations r
      JOIN users u ON r.user_id = u.id
      JOIN vehicles v ON r.vehicle_id = v.id
      ORDER BY r.id
    `);
    console.table(reservations.rows);

    console.log('\n--- PAYMENTS (with reservation & user) ---');
    const payments = await pool.query(`
      SELECT p.id, p.reservation_id, r.user_id, u.username, p.amount, p.status, p.payment_method
      FROM payments p
      JOIN reservations r ON p.reservation_id = r.id
      JOIN users u ON p.user_id = u.id
      ORDER BY p.id
    `);
    console.table(payments.rows);

    process.exit(0);
  } catch (error) {
    console.error('Error testing DB relations:', error);
    process.exit(1);
  }
}

testRelations();
