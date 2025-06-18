//// Server1 → Server2 데이터 전송용
//const net = require('net');
//const EncryptionUtil = require('./EncryptionUtil_Server'); // 암호화 유틸 경로 맞춰 수정
//
//const SERVER2_IP = '127.0.0.1';  // Server2 주소 (로컬에서 테스트 시 localhost)
//const SERVER2_PORT = 4001;       // 동기화용 포트 (Server2가 이 포트에서 수신 대기)
//
//function sendSyncToServer2(payload) {
//  const client = new net.Socket();
//
//  client.connect(SERVER2_PORT, SERVER2_IP, () => {
//    console.log('[SYNC] Server2 연결됨, 데이터 전송 시작');
//    const encryptedPayload = {
//      vmNumber: EncryptionUtil.encrypt(payload.vmNumber),
//      name:     EncryptionUtil.encrypt(payload.name),
//      price:    EncryptionUtil.encrypt(String(payload.price)),
//      stock:    EncryptionUtil.encrypt(String(payload.stock))
//    };
//
//    client.write(JSON.stringify(encryptedPayload));
//    client.end();
//  });
//
//  client.on('error', (err) => {
//    console.error('[SYNC] Server2 연결 실패:', err.message);
//  });
//}
//
//module.exports = { sendSyncToServer2 };
