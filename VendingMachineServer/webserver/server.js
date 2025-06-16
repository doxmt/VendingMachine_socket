// webserver/server.js

require('dotenv').config();
const express = require('express');
const { MongoClient } = require('mongodb');
const path = require('path');

const app = express();
const port = 3000;

// 정적 파일 (예: HTML) 제공
app.use(express.static(path.join(__dirname, 'public')));

app.get('/sales', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find().toArray();
    res.json(sales);
  } catch (err) {
    console.error('[서버] DB 조회 오류:', err);
    res.status(500).send('DB 조회 실패');
  } finally {
    await client.close();
  }
});

app.listen(port, () => {
  console.log(`[웹서버] http://localhost:${port} 에서 실행 중`);
});
