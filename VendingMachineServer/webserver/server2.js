require('dotenv').config();
const express = require('express');
const { MongoClient } = require('mongodb');
const path = require('path');
const crypto = require('crypto');
const net = require('net');
const { Mutex } = require('async-mutex');
const renameMutexMap = new Map();




const app = express();
const port = 3100;

const AES_KEY = Buffer.from(process.env.AES_KEY, 'utf-8');

function decryptAES(text) {
  const decipher = crypto.createDecipheriv('aes-128-ecb', AES_KEY, null);
  decipher.setAutoPadding(true);
  let decrypted = decipher.update(text, 'base64', 'utf8');
  decrypted += decipher.final('utf8');
  return decrypted;
}

function encryptAES(text) {
  const cipher = crypto.createCipheriv('aes-128-ecb', AES_KEY, null);
  cipher.setAutoPadding(true);
  let encrypted = cipher.update(text, 'utf8', 'base64');
  encrypted += cipher.final('base64');
  return encrypted;
}

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json());

function sendToSyncServer(payload) {
  return new Promise((resolve, reject) => {
    const client = new net.Socket();

    client.connect(9101, 'localhost', () => {
      client.write(JSON.stringify(payload));
    });

    client.on('data', (data) => {
      const message = data.toString();
      // 불필요한 로그 제거
      if (message === 'SYNC_OK') {
        client.destroy();
        resolve();
      } else {
        client.destroy();
        reject(new Error('동기화 실패 또는 알 수 없는 응답'));
      }
    });

    client.on('error', (err) => {
      // 실패 시 메시지만 출력
      console.error('동기화 오류:', err.message);
      reject(err);
    });
  });
}


app.get('/summary/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  const encryptedVm = encryptAES(req.params.vmNumber);
  const today = new Date().toISOString().slice(0, 10);
  const thisMonth = today.slice(0, 7);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find({ vmNumber: encryptedVm }).toArray();

    let daily = 0, monthly = 0, total = 0;
    for (const s of sales) {
      try {
        const price = parseInt(decryptAES(s.price));
        const date = decryptAES(s.date);
        total += price;
        if (date === today) daily += price;
        if (date.startsWith(thisMonth)) monthly += price;
      } catch {}
    }

    res.json({ daily, monthly, total });
  } catch (err) {
    res.status(500).send('통계 조회 실패');
  } finally {
    await client.close();
  }
});

app.get('/drink-sales/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  const encryptedVm = encryptAES(req.params.vmNumber);
  const today = new Date().toISOString().slice(0, 10);
  const thisMonth = today.slice(0, 7);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
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
      } catch {}
    }

    res.json(result);
  } catch (err) {
    res.status(500).send('음료별 매출 조회 실패');
  } finally {
    await client.close();
  }
});

app.get('/stock-status/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  const encryptedVm = encryptAES(req.params.vmNumber);
  const result = [];

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const inventory = await db.collection('inventory').find({ vmNumber: encryptedVm }).toArray();

    for (const doc of inventory) {
      try {
        const name = decryptAES(doc.name);
        const stock = parseInt(decryptAES(doc.stock));
        result.push({ name, stock });
      } catch {}
    }

    res.json(result);
  } catch (err) {
    res.status(500).send("재고 조회 실패");
  } finally {
    await client.close();
  }
});

const ServerMongoDBManager = require('./db/ServerMongoDBManager');
const serverMongoDBManager = ServerMongoDBManager.getInstance();

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
    const drinkNames = drinks.map(d => decryptAES(d.name));
    res.json(drinkNames);
  } catch (err) {
    res.status(500).send('음료 목록 조회 실패');
  } finally {
    await client.close();
  }
});

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
      } catch {}
    }

    const labels = Object.keys(revenueMap);
    const revenues = Object.values(revenueMap);
    res.json({ labels, revenues });
  } catch (err) {
    res.status(500).send('음료별 매출 비율 조회 실패');
  } finally {
    await client.close();
  }
});

app.get('/daily-sales/trend', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find().toArray();

    const dailyTotals = {};
    for (const s of sales) {
      try {
        const date = decryptAES(s.date);
        const price = parseInt(decryptAES(s.price));
        if (!dailyTotals[date]) dailyTotals[date] = 0;
        dailyTotals[date] += price;
      } catch {}
    }

    const sorted = Object.entries(dailyTotals)
      .sort(([a], [b]) => new Date(a) - new Date(b))
      .slice(-7);

    const labels = sorted.map(([date]) => date.slice(5));
    const data = sorted.map(([, total]) => total);
    res.json({ labels, data });
  } catch (err) {
    res.status(500).send('일별 매출 조회 실패');
  } finally {
    await client.close();
  }
});



const syncServer = net.createServer(socket => {
  socket.on('data', async data => {
    const raw = data.toString();

    // ✅ 백업서버의 생존 확인용 ping 요청 처리
    if (raw === 'PING') {
      socket.write('SERVER2_OK');
      return;
    }

    try {
      const payload = JSON.parse(raw);

      if (payload.type === 'init-sync') {
        socket.write('SYNC_OK');
        return;
      }

      console.log('[Server2] 동기화 수신:', payload);

      const { type, vmNumber } = payload;

      if (type === 'rename-drink') {
        await serverMongoDBManager.updateDrinkNameEverywhere(vmNumber, payload.oldName, payload.newName);
      } else if (type === 'insert-sale') {
        await serverMongoDBManager.insertSale(vmNumber, payload.drinkName, payload.price, payload.date);
      } else if (type === 'upsert-inventory') {
        await serverMongoDBManager.upsertInventory(vmNumber, payload.drinkName, payload.price, payload.stock, payload.date);
      } else if (type === 'update-change') {
        await serverMongoDBManager.updateChangeStorage(vmNumber, payload.changeMap);
      }

      socket.write('SYNC_OK');

    } catch (err) {
      console.error('[Server2] 동기화 처리 실패:', err.message);
      socket.write('SYNC_FAIL');
    }
  });
});


// 서버1에 초기 동기화 요청 보내기 함수 (디버그 메시지 추가)
async function trySyncWithServer1(retryInterval = 5000) {
  while (true) {
    console.log('[Server2] 서버1과 동기화 시도 중...');
    try {
      await sendToSyncServer({ type: 'init-sync', info: '서버2 시작시 초기 동기화 요청' });
      console.log('[Server2] Server1과 동기화 완료!');
      break;
    } catch (err) {
      console.log(`${retryInterval / 1000}초 후 재시도합니다...`);
      await new Promise(resolve => setTimeout(resolve, retryInterval));
    }
  }
}

app.listen(port, () => {
  console.log(`[Server2] http://localhost:${port} 에서 실행 중`);
  trySyncWithServer1();  // 명시적 호출
});

syncServer.listen(9102, () => {
   console.log('[Server2] 동기화 수신 서버 실행 중 (port 9102)');
 });