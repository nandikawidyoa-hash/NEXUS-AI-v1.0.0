function updateROI() {
  const count = parseInt(document.getElementById('custInput').value || '100', 10);
  document.getElementById('custVal').innerText = count;
  const savedHours = count * 3;
  document.getElementById('roiResult').innerText = 'Estimasi Peningkatan Efisiensi: +' + savedHours + ' Jam/Bulan';
}
function openDemoModal() {
  alert('Demo Website Bisnis Aktif & Berfungsi Normal!');
}
