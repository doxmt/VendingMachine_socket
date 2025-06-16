const vmButtons = document.querySelectorAll('aside button');
const sections = document.querySelectorAll('main section');
const title = document.querySelector('h1.title');


function showAllSections() {
  sections.forEach(section => {
    section.style.display = 'block';
  });
}

function hideAllSections() {
  sections.forEach(section => {
    section.style.display = 'none';
  });
}

vmButtons.forEach(button => {
  button.addEventListener('click', () => {
    showAllSections();
    vmButtons.forEach(btn => btn.classList.remove('active'));
    button.classList.add('active');

    const vmNumber = parseInt(button.dataset.vm); // ✅ 여기 고침
    loadSalesSummary(vmNumber);
     loadDrinkSales(vmNumber);
     loadStockStatus(vmNumber); // 재고 불러오기

  });
});

title.addEventListener('click', () => {
  hideAllSections();
  vmButtons.forEach(btn => btn.classList.remove('active'));
});

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

async function loadDrinkSales(vmNumber) {
  try {
    const res = await fetch(`/drink-sales/${vmNumber}`);
    const data = await res.json();

    const tbody = document.querySelector("#drink-sales tbody");
    tbody.innerHTML = ""; // 기존 행 삭제

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


