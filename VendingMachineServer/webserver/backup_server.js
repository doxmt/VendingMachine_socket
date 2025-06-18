const net = require('net');
const express = require('express');
const path = require('path');

const CHECK_INTERVAL = 10000; // 10초 간격
const PORT1 = 3000;
const PORT2 = 3100;
const SYNC_PORT1 = 9101;
const SYNC_PORT2 = 9102;

let server1Alive = true;
let server2Alive = true;
let app1 = null;
let app2 = null;

// 백업 서버 역할용 Express 앱 시작
function startBackupApp(port, label) {
  const app = express();
  app.use(express.static(path.join(__dirname, 'public')));
  app.get('/', (req, res) => {
    res.send(`[${label} 백업서버] 작동 중`);
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
setInterval(() => {
  // Server1 감시
  pingServer(
    SYNC_PORT1, 'SERVER1_OK',
    () => {
      if (!server1Alive) {
        console.log('[백업서버] Server1 응답 회복됨 ✅');
      } else {
        console.log('[백업서버] Server1 이상 없음 ✅'); // ✅ 정상 상태일 때도 출력
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
        console.log('[백업서버] Server2 이상 없음 ✅'); // ✅ 정상 상태일 때도 출력
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
