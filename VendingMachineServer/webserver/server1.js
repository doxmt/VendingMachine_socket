require('dotenv').config();
const express = require('express');
const { MongoClient } = require('mongodb');
const path = require('path');
const crypto = require('crypto');
const { Mutex } = require('async-mutex');
const renameMutexMap = new Map();
const net = require('net');


const app = express();
const port = 3000;

const AES_KEY = Buffer.from(process.env.AES_KEY, 'utf-8'); // 평문 키 그대로 사용


// AES 복호화 함수
function decryptAES(text) {
  const decipher = crypto.createDecipheriv('aes-128-ecb', AES_KEY, null); // ✅ 변수명 수정
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

// 정적 파일 제공
app.use(express.static(path.join(__dirname, 'public')));

// ✅ 매출 요약
app.get('/summary/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  const encryptedVm = encryptAES(req.params.vmNumber);  // ⚠️ 평문 → 암호화
  const today = new Date().toISOString().slice(0, 10);
  const thisMonth = today.slice(0, 7);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find({ vmNumber: encryptedVm }).toArray();

    console.log(`[요청] vmNumber: ${req.params.vmNumber}`);
    console.log(`[매출 데이터 수]: ${sales.length}`);

    let daily = 0, monthly = 0, total = 0;

    for (const s of sales) {
      try {
        const price = parseInt(decryptAES(s.price));
        const date = decryptAES(s.date);

        total += price;
        if (date === today) daily += price;
        if (date.startsWith(thisMonth)) monthly += price;
      } catch (err) {
        console.warn('[WARN] 개별 매출 복호화 실패:', s, err.message);
      }
    }

    res.json({ daily, monthly, total });
  } catch (err) {
    console.error('[서버] 통계 계산 오류:', err);
    res.status(500).send('통계 조회 실패');
  } finally {
    await client.close();
  }
});

// ✅ 음료별 매출
app.get('/drink-sales/:vmNumber', async (req, res) => {

  const client = new MongoClient(process.env.MONGO_URI);
  const encryptedVm = encryptAES(req.params.vmNumber);
  const today = new Date().toISOString().slice(0, 10);
  const thisMonth = today.slice(0, 7);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find({ vmNumber: encryptedVm }).toArray();

    console.log(`[음료별 매출 요청] vmNumber: ${req.params.vmNumber}`);
    console.log(`[매출 데이터 수]: ${sales.length}`);

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
        console.warn("복호화 오류:", e.message);
      }
    }

    console.log(`[음료별 매출 결과]:`, result);
    res.json(result);
  } catch (err) {
    console.error('[서버] 음료별 매출 조회 오류:', err);
    res.status(500).send('음료별 매출 조회 실패');
  } finally {
    await client.close();
  }
});

// ✅ 재고 현황
app.get('/stock-status/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  const encryptedVm = encryptAES(req.params.vmNumber);
  const result = [];

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const inventory = await db.collection('inventory').find({ vmNumber: encryptedVm }).toArray();

    console.log(`[재고조회] vmNumber=${req.params.vmNumber}, 개수=${inventory.length}`);

    for (const doc of inventory) {
      try {
        const name = decryptAES(doc.name);
        const stock = parseInt(decryptAES(doc.stock));
        result.push({ name, stock });
      } catch (e) {
        console.warn("재고 복호화 실패:", e.message);
      }
    }

    res.json(result);
  } catch (err) {
    console.error("[서버] 재고 현황 오류:", err);
    res.status(500).send("재고 조회 실패");
  } finally {
    await client.close();
  }
});

const ServerMongoDBManager = require('./db/ServerMongoDBManager'); // 실제 경로 맞게 수정

const serverMongoDBManager = ServerMongoDBManager.getInstance();

app.use(express.json()); // JSON body 파싱용 미들웨어 (이미 있으면 생략 가능)

function getRenameMutex(vmNumber, drinkName) {
  const key = `${vmNumber}:${drinkName}`;
  if (!renameMutexMap.has(key)) {
    renameMutexMap.set(key, new Mutex());
  }
  return renameMutexMap.get(key);
}

app.post('/rename-drink', async (req, res) => {
  const { vmNumber, oldName, newName } = req.body;
  if (!vmNumber || !oldName || !newName) {
    return res.status(400).send('필수 파라미터 누락');
  }

  const mutex = getRenameMutex(vmNumber, oldName);
  await mutex.runExclusive(async () => {
    try {
      await serverMongoDBManager.updateDrinkNameEverywhere(vmNumber, oldName, newName);
      sendToSyncServer({ type: 'rename-drink', vmNumber, oldName, newName }); // 양방향 동기화
      res.status(200).send('변경 완료');
    } catch (err) {
      console.error('음료 이름 변경 실패:', err);
      res.status(500).send('서버 오류');
    }
  });
});

app.get('/drink-list/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  const encryptedVm = encryptAES(req.params.vmNumber);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const drinks = await db.collection('drinks').find({ vmNumber: encryptedVm }).toArray();

    // 음료명만 복호화해서 배열로 반환
    const drinkNames = drinks.map(d => decryptAES(d.name));

    res.json(drinkNames);
  } catch (err) {
    console.error('[서버] 음료 목록 조회 실패:', err);
    res.status(500).send('음료 목록 조회 실패');
  } finally {
    await client.close();
  }
});


// ✅ 전체 자판기 기준 음료별 매출 합산 (도넛차트용)
app.get('/drink-sales/revenue/all', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find().toArray();

    const revenueMap = {};

    for (const s of sales) {
      try {
        const name = decryptAES(s.drinkName);
        const price = parseInt(decryptAES(s.price));

        revenueMap[name] = (revenueMap[name] || 0) + price;
      } catch (e) {
        console.warn('[도넛용 복호화 실패]', e.message);
      }
    }

    const labels = Object.keys(revenueMap);
    const revenues = Object.values(revenueMap);

    res.json({ labels, revenues });
  } catch (err) {
    console.error('[서버] 음료별 매출 비율 오류:', err);
    res.status(500).send('음료별 매출 비율 조회 실패');
  } finally {
    await client.close();
  }
});

// ✅ 최근 7일간 일별 총 매출 조회
app.get('/daily-sales/trend', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find().toArray();

    const dailyTotals = {};

    for (const s of sales) {
      try {
        const date = decryptAES(s.date);                    // '2025-06-17'
        const price = parseInt(decryptAES(s.price));

        if (!dailyTotals[date]) dailyTotals[date] = 0;
        dailyTotals[date] += price;
      } catch (e) {
        console.warn('[일별매출 복호화 실패]', e.message);
      }
    }

    // 최근 7일 정렬
    const sorted = Object.entries(dailyTotals)
      .sort(([a], [b]) => new Date(a) - new Date(b))
      .slice(-7);

    const labels = sorted.map(([date]) => date.slice(5)); // '06-17'
    const data = sorted.map(([, total]) => total);

    res.json({ labels, data });

  } catch (err) {
    console.error('[일별 매출 트렌드 오류]', err);
    res.status(500).send('일별 매출 조회 실패');
  } finally {
    await client.close();
  }
});


function sendToSyncServer(payload) {
  return new Promise((resolve, reject) => {
    const client = new net.Socket();

    client.connect(9102, 'localhost', () => {  // ✅ Server2의 sync 포트는 9102
      client.write(JSON.stringify(payload));
    });

    client.on('data', (data) => {
      const message = data.toString();
      if (message === 'SYNC_OK') {
        client.destroy();
        resolve();
      } else {
        client.destroy();
        reject(new Error('동기화 실패 또는 알 수 없는 응답'));
      }
    });

    client.on('error', (err) => {
      console.error('[Server1 → Server2 동기화 오류]', err.message);
      reject(err);
    });
  });
}


// ✅ 서버 기동 후 Server2와 초기 동기화 시도
async function trySyncWithServer2(retryInterval = 5000) {
  while (true) {
    try {
      await sendToSyncServer({ type: 'init-sync', info: '서버1 시작시 초기 동기화 요청' });
      console.log('Server2와 동기화 완료!');
      break;
    } catch (err) {
      console.error('Server2 동기화 실패:', err.message);
      console.log(`${retryInterval / 1000}초 후 재시도합니다...`);
      await new Promise(resolve => setTimeout(resolve, retryInterval));
    }
  }
}



// ✅ [추가] 백업서버 ping 감지를 위한 TCP 동기화 수신 서버 (포트 9101)
const syncServer = net.createServer(socket => {
  socket.on('data', async data => {
    const raw = data.toString();

    if (raw === 'PING') {
      socket.write('SERVER1_OK');
      return;
    }

    try {
      const payload = JSON.parse(raw);

      if (payload.type === 'init-sync') {
        socket.write('SYNC_OK');
        return;
      }

      console.log('[Server1] 다른 동기화 요청 수신:', payload);

      // 이 부분에 추가적인 동기화 처리 로직 구현 가능 (필요 시)

      socket.write('SYNC_OK'); // 기본 성공 응답
    } catch (err) {
      console.error('[Server1] 동기화 처리 실패:', err.message);
      socket.write('SYNC_FAIL');
    }
  });
});

syncServer.listen(9101, () => {
  console.log('[Server1] 동기화 수신 서버 실행 중 (port 9101)');
});

app.listen(port, () => {
  console.log(`[웹서버] http://localhost:${port} 에서 실행 중`);
  trySyncWithServer2();
});

