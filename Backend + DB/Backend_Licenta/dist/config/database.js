"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const pg_1 = require("pg");
const pool = new pg_1.Pool({
    user: 'postgres',
    host: 'localhost',
    database: 'licenta',
    password: 'Mihalache6784',
    port: 5432,
});
// Test conexiune la pornire
pool.query('SELECT NOW()', (err, res) => {
    if (err) {
        console.error('Eroare la conectarea la baza de date:', err);
        process.exit(1); // Oprim serverul dacă nu putem conecta la baza de date
    }
    else {
        console.log('Conectat cu succes la baza de date PostgreSQL');
    }
});
exports.default = pool;
