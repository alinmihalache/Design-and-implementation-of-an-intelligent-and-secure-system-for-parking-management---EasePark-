import app from './app';

const port = 3000;

app.listen(port, () => {
  console.log(`Serverul rulează la http://localhost:${port}`);
  console.log('backend merge');
});