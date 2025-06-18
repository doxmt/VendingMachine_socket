// 웹 대시보드 JS (암호화 제거 버전)

const vmButtons = document.querySelectorAll('aside button');
const sections = document.querySelectorAll('main section');
const title = document.querySelector('h1.title');

function showAllSections() {
  sections.forEach(section => {
    if (section.id !== "welcome") {
      section.style.display = 'block';
    }
  });
  const welcome = document.getElementById("welcome");
  if (welcome) welcome.style.display = 'none';
}

function hideAllSections() {
  sections.forEach(section => {
    if (section.id !== "welcome") {
      section.style.display = 'none';
    }
  });
  const welcome = document.getElementById("welcome");
  if (welcome) welcome.style.display = 'block';
}


function getSelectedVendingMachineNumber() {
  const activeBtn = document.querySelector('aside button.active');
  return activeBtn ? activeBtn.dataset.vm : null;
}


// 자판기 버튼 클릭 시 이벤트 처리
vmButtons.forEach(button => {
  button.addEventListener('click', () => {
    showAllSections();
    vmButtons.forEach(btn => btn.classList.remove('active'));
    button.classList.add('active');

    const vmNumber = button.dataset.vm;  // ✅ 그대로 사용
    loadSalesSummary(vmNumber);
    loadDrinkSales(vmNumber);
    loadStockStatus(vmNumber);
    loadLowStockAlerts(vmNumber);
    loadDrinkOptions(vmNumber);
  });
});

// 타이틀 클릭 시 섹션 숨기기
title.addEventListener('click', () => {
  hideAllSections();
  vmButtons.forEach(btn => btn.classList.remove('active'));
});

// ✅ 매출 요약 불러오기 (암호화 X)
async function loadSalesSummary(vmNumber) {
  try {
    const response = await fetch(`/summary/${vmNumber}`);
    const data = await response.json();

    document.getElementById("daily").textContent = data.daily + "원";
    document.getElementById("monthly").textContent = data.monthly + "원";
    document.getElementById("total").textContent = data.total + "원";
  } catch (err) {
    console.error("매출 요약 로딩 실패:", err);
  }
}

// ✅ 음료별 매출 불러오기 (암호화 X)
async function loadDrinkSales(vmNumber) {
  try {
    const res = await fetch(`/drink-sales/${vmNumber}`);
    const data = await res.json();

    const tbody = document.querySelector("#drink-sales tbody");
    tbody.innerHTML = "";

    for (const drinkName in data) {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${drinkName}</td>
        <td>${data[drinkName].daily}원</td>
        <td>${data[drinkName].monthly}원</td>
        <td>${data[drinkName].total}원</td>
      `;
      tbody.appendChild(row);
    }
  } catch (err) {
    console.error("음료별 매출 로딩 실패:", err);
  }
}

// ✅ 재고 현황 불러오기 (암호화 X)
async function loadStockStatus(vmNumber) {
  try {
    const res = await fetch(`/stock-status/${vmNumber}`);
    const data = await res.json();

    const tbody = document.querySelector("#stock-status tbody");
    tbody.innerHTML = "";

    for (const item of data) {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${item.name}</td>
        <td>${item.stock}개</td>
      `;
      tbody.appendChild(row);
    }
  } catch (err) {
    console.error("재고 현황 로딩 실패:", err);
  }
}

// 재고 부족 알람
async function loadLowStockAlerts(vmNumber) {
  try {
    const res = await fetch(`/stock-status/${vmNumber}`);
    const data = await res.json();

    const tbody = document.querySelector("#stock-status tbody");
    tbody.innerHTML = "";

    const alertDiv = document.getElementById("low-stock-alerts");
    alertDiv.innerHTML = ""; // 항상 초기화

    const lowStockItems = [];

    for (const item of data) {
      const row = document.createElement("tr");

      // 재고 0개일 경우 강조 표시 및 알림용 저장
      if (item.stock === 0) {
        row.style.color = "red";
        lowStockItems.push(item.name);
      }

      row.innerHTML = `
        <td>${item.name}</td>
        <td>${item.stock}개</td>
      `;
      tbody.appendChild(row);
    }

    // 조건에 따라 알림 표시 또는 제거
    if (lowStockItems.length > 0) {
      alertDiv.innerHTML = `⚠️ <strong>품절</strong>된 음료: ${lowStockItems.join(", ")}`;
    } else {
      alertDiv.innerHTML = ""; // 알림 제거
    }

  } catch (err) {
    console.error("재고 현황 로딩 실패:", err);
  }
}

// 음료 이름 변경
async function loadDrinkOptions(vmNumber) {
  try {
    const res = await fetch(`/drink-list/${vmNumber}`);
    const drinkNames = await res.json();
    const select = document.getElementById('drink-select');
    select.innerHTML = '';

    drinkNames.forEach(drinkName => {
      const option = document.createElement('option');
      option.value = drinkName;
      option.textContent = drinkName;
      select.appendChild(option);
    });
  } catch (err) {
    console.error('음료 목록 불러오기 실패:', err);
  }
}


// 폼 제출 이벤트 핸들러
document.getElementById('rename-form').addEventListener('submit', async (e) => {
  e.preventDefault();

  const vmNumber = getSelectedVendingMachineNumber(); // 웹페이지에서 현재 선택된 자판기 번호 가져오는 함수 필요
  const oldName = document.getElementById('drink-select').value;
  const newName = document.getElementById('new-drink-name').value.trim();

  if (!newName) {
    alert('새 음료 이름을 입력하세요.');
    return;
  }

  try {
    const res = await fetch('/rename-drink', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ vmNumber, oldName, newName })
    });

    if (res.ok) {
      alert('음료 이름이 성공적으로 변경되었습니다.');
      // 음료 목록과 매출, 재고 등 다시 불러오기
      await loadDrinkOptions(vmNumber);
      await loadDrinkSales(vmNumber);
      await loadStockStatus(vmNumber);
    } else {
      const errorText = await res.text();
      alert('이름 변경 실패: ' + errorText);
    }
  } catch (err) {
    alert('서버 통신 오류: ' + err.message);
  }
});

document.addEventListener('DOMContentLoaded', async () => {
  hideAllSections();  // 섹션 숨기고 welcome만 보이게

  // ─── 1) 자판기별 매출 비교 (Bar) ────────────────────────────────
  const barCtx = document.getElementById('salesBarChart').getContext('2d');
  const vendingIds = ['VM1', 'VM2', 'VM3', 'VM4'];

  // 각 자판기별 total 매출을 병렬로 가져오기
  const summaries = await Promise.all(
    vendingIds.map(vm =>
      fetch(`/summary/${vm}`)
        .then(res => res.json())
        .then(data => data.total)
        .catch(() => 0)  // 실패 시 0으로
    )
  );

  new Chart(barCtx, {
    type: 'bar',
    data: {
      labels: vendingIds,
      datasets: [{
        label: '총 매출 (원)',
        data: summaries,  // ← 동적 데이터
        backgroundColor: ['#42a5f5', '#66bb6a', '#ffa726', '#ef5350']
      }]
    },
    options: {
      responsive: true,
      scales: { y: { beginAtZero: true } },
      plugins: { legend: { display: false } }
    }
  });
const donutCtx = document.getElementById('drinkDonutChart').getContext('2d');

  try {
    const res = await fetch('/drink-sales/revenue/all');
    const json = await res.json();

    new Chart(donutCtx, {
      type: 'doughnut',
      data: {
        labels: json.labels,
        datasets: [{
          data: json.revenues,
          backgroundColor: ['#FF6384', '#36A2EB', '#FFCE56', '#66bb6a', '#AB47BC']
        }]
      },
      options: {
        responsive: true,
        plugins: {
          legend: { position: 'bottom' },
          tooltip: {
            callbacks: {
              label: function (context) {
                const value = context.parsed;
                return `${context.label}: ${value.toLocaleString()}원`;
              }
            }
          }
        }
      }
    });
  } catch (e) {
    console.error('도넛차트 로드 실패:', e);
  }

// ─── 3) 일별 매출 추이 (Line Chart) ───────────────────────────────
const dailyCtx = document.getElementById('dailySalesChart').getContext('2d');

try {
  const res = await fetch('/daily-sales/trend');
  const json = await res.json();

  new Chart(dailyCtx, {
    type: 'line',
    data: {
      labels: json.labels,  // ['06-12', '06-13', ...]
      datasets: [{
        label: '일일 매출 (원)',
        data: json.data,
        fill: false,
        borderColor: '#42a5f5',
        tension: 0.3,
        pointRadius: 5,
        pointBackgroundColor: '#42a5f5'
      }]
    },
    options: {
      responsive: true,
      scales: {
        y: {
          beginAtZero: true,
          ticks: {
            callback: value => value.toLocaleString() + '원'
          }
        }
      },
      plugins: {
        tooltip: {
          callbacks: {
            label: function(context) {
              return `${context.parsed.y.toLocaleString()}원`;
            }
          }
        }
      }
    }
  });

} catch (e) {
  console.error('일별 매출 차트 로드 실패:', e);
}





});
