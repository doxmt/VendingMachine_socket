// test.js
require('dotenv').config(); // .env 파일에서 환경 변수 로드

const key = process.env.AES_KEY;

if (!key) {
  console.error("❌ AES_KEY 환경변수가 설정되어 있지 않습니다.");
  process.exit(1);
}

const buffer = Buffer.from(key.trim(), 'base64');
console.log("🔐 base64 디코딩된 AES_KEY 길이:", buffer.length, "bytes");

if (buffer.length !== 16) {
  console.warn("⚠️ AES-128-ECB 모드에서는 정확히 16바이트 키가 필요합니다.");
} else {
  console.log("✅ 키 길이 정상 (16바이트)");
}
