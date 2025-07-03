import { Client } from 'pg';
import dotenv from 'dotenv';

dotenv.config();

// Configurația bazei de date
const dbConfig = {
  user: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD || 'Mihalache6784',
  host: process.env.DB_SERVER || 'localhost',
  port: Number(process.env.DB_PORT) || 5432,
  database: process.env.DB_NAME || 'licenta',
};

async function testDatabase() {
  const client = new Client(dbConfig);

  try {
    console.log('🔌 Conectare la baza de date...');
    await client.connect();
    console.log('✅ Conectare reușită!');

    // Testează conexiunea
    const timeResult = await client.query('SELECT NOW() as current_time');
    console.log('📅 Timp server:', timeResult.rows[0].current_time);

    // Verifică tabelele existente
    const tablesResult = await client.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    `);

    console.log('📋 Tabele disponibile:');
    tablesResult.rows.forEach(table => {
      console.log(`  - ${table.table_name}`);
    });

    // Caută tabelul de utilizatori
    const userTables = tablesResult.rows.filter(t =>
      t.table_name.toLowerCase().includes('user') ||
      t.table_name.toLowerCase().includes('utilizator')
    );

    if (userTables.length > 0) {
      const tableName = userTables[0].table_name;
      console.log(`\n👤 Găsit tabel utilizatori: ${tableName}`);

      // Structura coloanelor
      const columnsResult = await client.query(`
        SELECT column_name, data_type 
        FROM information_schema.columns 
        WHERE table_name = $1
      `, [tableName]);

      console.log('📝 Coloane:');
      columnsResult.rows.forEach(col => {
        console.log(`  - ${col.column_name} (${col.data_type})`);
      });

      // Verifică datele
      const usersResult = await client.query(`SELECT * FROM ${tableName} LIMIT 5`);
      console.log(`\n👥 Utilizatori (primii 5):`);
      console.log(usersResult.rows);

      // Caută utilizatorul test
      const testUser = await client.query(
        `SELECT * FROM ${tableName} WHERE username = $1`,
        ['test']
      );

      if (testUser.rows.length > 0) {
        console.log('\n🎯 Utilizator test găsit:');
        console.log(testUser.rows[0]);
      } else {
        console.log('\n❌ Utilizatorul test nu există');
        console.log('💡 Poți adăuga unul cu:');
        console.log(`INSERT INTO ${tableName} (username, password, email) VALUES ('test', '1234', 'test@example.com')`);
      }
    } else {
      console.log('\n❌ Nu am găsit tabele de utilizatori');
      console.log('💡 Verifică manual tabelele disponibile mai sus');
    }

  } catch (error) {
    console.error('❌ Eroare:', error);
  } finally {
    await client.end();
    console.log('\n🔚 Conexiune închisă');
  }
}

// Rulează testul
testDatabase();
