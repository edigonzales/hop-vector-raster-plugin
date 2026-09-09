// Local, dependency-free controls; the gallery also works when opened as file://.
const sizeInputs = document.querySelectorAll('input[name="size"]');
const surfaceInputs = document.querySelectorAll('input[name="surface"]');
const grayscale = document.getElementById('grayscale');
const search = document.getElementById('search');
const cards = [...document.querySelectorAll('.icon-card')];

function updateView() {
  document.documentElement.style.setProperty('--size', `${document.querySelector('input[name="size"]:checked').value}px`);
  document.body.dataset.surface = document.querySelector('input[name="surface"]:checked').value;
  document.body.classList.toggle('grayscale', grayscale.checked);
}

function updateSearch() {
  const terms = search.value.trim().toLocaleLowerCase('de').split(/\s+/).filter(Boolean);
  let visible = 0;
  for (const card of cards) {
    const text = card.dataset.search.toLocaleLowerCase('de');
    card.hidden = !terms.every(term => text.includes(term));
    if (!card.hidden) visible++;
  }
  document.getElementById('result-count').textContent = `${visible} ${visible === 1 ? 'Icon' : 'Icons'}`;
  document.getElementById('empty-state').hidden = visible !== 0;
}

[...sizeInputs, ...surfaceInputs, grayscale].forEach(input => input.addEventListener('change', updateView));
search.addEventListener('input', updateSearch);
updateView();
updateSearch();
