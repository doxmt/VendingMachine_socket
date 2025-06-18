require('dotenv').config();
const express = require('express');
const { MongoClient } = require('mongodb');
const path = require('path');
const crypto = require('crypto');

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

app.post('/rename-drink', async (req, res) => {
  const { vmNumber, oldName, newName } = req.body;

  if (!vmNumber || !oldName || !newName) {
    res.status(400).send('필수 파라미터 누락');
    return;
  }

  try {
    await serverMongoDBManager.updateDrinkNameEverywhere(vmNumber, oldName, newName);
    res.status(200).send('변경 완료');
  } catch (err) {
    console.error('음료 이름 변경 실패:', err);
    res.status(500).send('서버 오류');
  }
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




app.listen(port, () => {
  console.log(`[웹서버] http://localhost:${port} 에서 실행 중`);

});


