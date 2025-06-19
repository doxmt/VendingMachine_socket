require('dotenv').config();
const net = require('net');
const express = require('express');
const path = require('path');
const { MongoClient } = require('mongodb');
const crypto = require('crypto');

const CHECK_INTERVAL = 10000; // 10초 간격
const PORT1 = 3000;           // Server1 백업서버 포트
const PORT2 = 3100;           // Server2 백업서버 포트
const SYNC_PORT1 = 9101;
const SYNC_PORT2 = 9102;

const AES_KEY = Buffer.from(process.env.AES_KEY, 'utf-8');
const MONGO_URI = process.env.MONGO_URI;

let server1Alive = true;
let server2Alive = true;
let app1 = null;
let app2 = null;

// AES 복호화 함수
function decryptAES(text) {
  const decipher = crypto.createDecipheriv('aes-128-ecb', AES_KEY, null);
  decipher.setAutoPadding(true);
  let decrypted = decipher.update(text, 'base64', 'utf8');
  decrypted += decipher.final('utf8');
  return decrypted;
}
// AES 암호화 함수
function encryptAES(text) {
  const cipher = crypto.createCipheriv('aes-128-ecb', AES_KEY, null);
  cipher.setAutoPadding(true);
  let encrypted = cipher.update(text, 'utf8', 'base64');
  encrypted += cipher.final('base64');
  return encrypted;
}

// MongoClient 전역 생성 및 한 번 연결
const mongoClient = new MongoClient(MONGO_URI);

async function startBackupApp(port, label) {
  const app = express();
  app.use(express.static(path.join(__dirname, 'public')));
  app.use(express.json());

  // 기본 루트
  app.get('/', (req, res) => {
    res.send(`[${label} 백업서버] 작동 중`);
  });

  // 매출 요약 API
  app.get('/summary/:vmNumber', async (req, res) => {
    try {
      const db = mongoClient.db('vending_machine_server');
      const encryptedVm = encryptAES(req.params.vmNumber);
      const today = new Date().toISOString().slice(0, 10);
      const thisMonth = today.slice(0, 7);

      const sales = await db.collection('sales').find({ vmNumber: encryptedVm }).toArray();

      let daily = 0, monthly = 0, total = 0;
      for (const s of sales) {
        try {
          const price = parseInt(decryptAES(s.price));
          const date = decryptAES(s.date);

          total += price;
          if (date === today) daily += price;
          if (date.startsWith(thisMonth)) monthly += price;
        } catch (e) {
          console.warn(`[${label}] 복호화 오류:`, e.message);
        }
      }
      res.json({ daily, monthly, total });
    } catch (err) {
      console.error(`[${label}] 매출 요약 조회 실패:`, err);
      res.status(500).send('매출 요약 조회 실패');
    }
  });

  // 음료별 매출 API
  app.get('/drink-sales/:vmNumber', async (req, res) => {
    try {
      const db = mongoClient.db('vending_machine_server');
      const encryptedVm = encryptAES(req.params.vmNumber);
      const today = new Date().toISOString().slice(0, 10);
      const thisMonth = today.slice(0, 7);

      const sales = await db.collection('sales').find({ vmNumber: encryptedVm }).toArray();
      const result = {};

      for (const s of sales) {
        try {
          const name = decryptAES(s.drinkName);
          const price = parseInt(decryptAES(s.price));
          const date = decryptAES(s.date);

          if (!result[name]) {
            result[name] = { daily: 0, monthly: 0, total: 0 };
          }

          result[name].total += price;
          if (date === today) result[name].daily += price;
          if (date.startsWith(thisMonth)) result[name].monthly += price;
        } catch (e) {
          console.warn(`[${label}] 음료별 복호화 오류:`, e.message);
        }
      }

      res.json(result);
    } catch (err) {
      console.error(`[${label}] 음료별 매출 조회 실패:`, err);
      res.status(500).send('음료별 매출 조회 실패');
    }
  });

  // 재고 현황 API
  app.get('/stock-status/:vmNumber', async (req, res) => {
    try {
      const db = mongoClient.db('vending_machine_server');
      const encryptedVm = encryptAES(req.params.vmNumber);

      const inventory = await db.collection('inventory').find({ vmNumber: encryptedVm }).toArray();
      const result = [];

      for (const doc of inventory) {
        try {
          const name = decryptAES(doc.name);
          const stock = parseInt(decryptAES(doc.stock));
          result.push({ name, stock });
        } catch (e) {
          console.warn(`[${label}] 재고 복호화 실패:`, e.message);
        }
      }

      res.json(result);
    } catch (err) {
      console.error(`[${label}] 재고 조회 실패:`, err);
      res.status(500).send('재고 조회 실패');
    }
  });
  // 백업서버 app 생성부 안에 추가 (startBackupApp 함수 내)
  app.get('/daily-sales/trend', async (req, res) => {
    try {
      const db = mongoClient.db('vending_machine_server');
      const sales = await db.collection('sales').find().toArray();

      const dailyTotals = {};

      for (const s of sales) {
        try {
          const date = decryptAES(s.date);                    // 'YYYY-MM-DD'
          const price = parseInt(decryptAES(s.price));

          if (!dailyTotals[date]) dailyTotals[date] = 0;
          dailyTotals[date] += price;
        } catch (e) {
          console.warn('[백업서버] 일별매출 복호화 실패:', e.message);
        }
      }

      const sorted = Object.entries(dailyTotals)
        .sort(([a], [b]) => new Date(a) - new Date(b))
        .slice(-7);

      const labels = sorted.map(([date]) => date.slice(5)); // MM-DD
      const data = sorted.map(([, total]) => total);

      res.json({ labels, data });

    } catch (err) {
      console.error('[백업서버] 일별 매출 트렌드 오류:', err);
      res.status(500).send('일별 매출 조회 실패');
    }
  });

app.get('/drink-sales/revenue/all', async (req, res) => {
  try {
    const db = mongoClient.db('vending_machine_server');
    const sales = await db.collection('sales').find().toArray();

    const revenueMap = {};

    for (const s of sales) {
      try {
        const name = decryptAES(s.drinkName);
        const price = parseInt(decryptAES(s.price));

        revenueMap[name] = (revenueMap[name] || 0) + price;
      } catch (e) {
        console.warn('[백업서버] 도넛용 복호화 실패', e.message);
      }
    }

    const labels = Object.keys(revenueMap);
    const revenues = Object.values(revenueMap);

    res.json({ labels, revenues });
  } catch (err) {
    console.error('[백업서버] 음료별 매출 비율 오류:', err);
    res.status(500).send('음료별 매출 비율 조회 실패');
  }
});


  const server = app.listen(port, () => {
    console.log(`[${label} 백업서버] http://localhost:${port} 에서 실행 중`);
  });

  return server;
}

// 서버 상태 점검 함수 (PING)
function pingServer(syncPort, expectedResponse, onAlive, onDead) {
  const socket = new net.Socket();
  socket.setTimeout(2000);
  socket.connect(syncPort, 'localhost', () => socket.write('PING'));

  socket.on('data', data => {
    const msg = data.toString();
    if (msg === expectedResponse) {
      socket.destroy();
      onAlive();
    } else {
      socket.destroy();
      onDead();
    }
  });

  socket.on('timeout', () => { socket.destroy(); onDead(); });
  socket.on('error', () => { socket.destroy(); onDead(); });
}

async function main() {
  try {
    await mongoClient.connect();
    console.log('MongoDB 연결 성공');

    setInterval(() => {
      // Server1 감시
      pingServer(
        SYNC_PORT1, 'SERVER1_OK',
        () => {
          if (!server1Alive) {
            console.log('[백업서버] Server1 응답 회복됨 ✅');
          } else {
            console.log('[백업서버] Server1 이상 없음 ✅');
          }
          server1Alive = true;

          if (app1) {
            console.log('[백업서버] Server1 복구됨 → 대체 종료');
            app1.close();
            app1 = null;
          }
        },
        () => {
          if (server1Alive) {
            console.error('[백업서버] Server1 응답 없음 ❌ → 백업서버가 대체 실행');
            app1 = startBackupApp(PORT1, 'Server1');
          } else {
            console.log('[백업서버] Server1 대체 실행 중...');
          }
          server1Alive = false;
        }
      );

      // Server2 감시
      pingServer(
        SYNC_PORT2, 'SERVER2_OK',
        () => {
          if (!server2Alive) {
            console.log('[백업서버] Server2 응답 회복됨 ✅');
          } else {
            console.log('[백업서버] Server2 이상 없음 ✅');
          }
          server2Alive = true;

          if (app2) {
            console.log('[백업서버] Server2 복구됨 → 대체 종료');
            app2.close();
            app2 = null;
          }
        },
        () => {
          if (server2Alive) {
            console.error('[백업서버] Server2 응답 없음 ❌ → 백업서버가 대체 실행');
            app2 = startBackupApp(PORT2, 'Server2');
          } else {
            console.log('[백업서버] Server2 대체 실행 중...');
          }
          server2Alive = false;
        }
      );

    }, CHECK_INTERVAL);

    console.log(`[백업서버] Server1, Server2 상태 감시 시작 (${CHECK_INTERVAL / 1000}초 주기)`);

  } catch (err) {
    console.error('MongoDB 연결 실패:', err);
    process.exit(1);
  }
}

main();
