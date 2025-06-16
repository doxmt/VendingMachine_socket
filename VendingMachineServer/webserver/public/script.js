
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

  // 자판기 버튼 클릭 시 섹션 보이기 + 선택 표시
  vmButtons.forEach(button => {
    button.addEventListener('click', () => {
      showAllSections();
      vmButtons.forEach(btn => btn.classList.remove('active'));
      button.classList.add('active');
    });
  });

  // h1 클릭 시 섹션 숨기고 버튼 선택 해제
  title.addEventListener('click', () => {
    hideAllSections();
    vmButtons.forEach(btn => btn.classList.remove('active'));
  });
