// webserver/server.js

require('dotenv').config();
const express = require('express');
const { MongoClient } = require('mongodb');
const path = require('path');

const app = express();
const port = 3000;


const crypto = require('crypto');
const key = Buffer.from(process.env.AES_KEY); // 16바이트

//복호화
function decryptAES(text) {
  const decipher = crypto.createDecipheriv('aes-128-ecb', key, null);
  decipher.setAutoPadding(true);
  let decrypted = decipher.update(text, 'base64', 'utf8');
  decrypted += decipher.final('utf8');
  return decrypted;
}


// 정적 파일 (예: HTML) 제공
app.use(express.static(path.join(__dirname, 'public')));

// webserver/server.js

app.get('/summary/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);

  const vmNumber = req.params.vmNumber;  // ✅ 문자열
  const today = new Date().toISOString().slice(0, 10);
  const thisMonth = today.slice(0, 7);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find({ vmNumber }).toArray();

    console.log(`[요청] vmNumber: ${vmNumber}`);
    console.log(`[매출 데이터 수]: ${sales.length}`);

    let daily = 0, monthly = 0, total = 0;

    for (const s of sales) {
      try {
        const price = parseInt(decryptAES(s.price));
        const date = decryptAES(s.date);

        console.log(`[매출] 가격: ${price}원 | 날짜: ${date} | 오늘: ${today}`);

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

app.get('/drink-sales/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);

  const vmNumber = req.params.vmNumber;  // ✅ 문자열 그대로 사용
  const today = new Date().toISOString().slice(0, 10);
  const thisMonth = today.slice(0, 7);

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const sales = await db.collection('sales').find({ vmNumber }).toArray();

    console.log(`[음료별 매출 요청] vmNumber: ${vmNumber}`);
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

app.get('/stock-status/:vmNumber', async (req, res) => {
  const client = new MongoClient(process.env.MONGO_URI);
  const vmNumber = req.params.vmNumber; // 문자열로 유지
  const result = [];

  try {
    await client.connect();
    const db = client.db('vending_machine_server');
    const inventory = await db.collection('inventory').find({ vmNumber }).toArray();

    console.log(`[재고조회] vmNumber=${vmNumber}, 개수=${inventory.length}`);

    for (const doc of inventory) {
      try {
        const name = decryptAES(doc.name);   // ✅ 복호화 필요
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










app.listen(port, () => {
  console.log(`[웹서버] http://localhost:${port} 에서 실행 중`);
});
