// Giả lập ESP32 để thử hệ thống khi chưa có phần cứng (cần Node.js 18+).
// Cách chạy: node tools/simulate-esp32.mjs <API_KEY> [giây giữa các lần gửi] [địa chỉ server]
// Ví dụ:    node tools/simulate-esp32.mjs 3f9a...c1 5 http://localhost:8080
const key = process.argv[2];
const every = Number(process.argv[3] || 5) * 1000;
const base = process.argv[4] || 'http://localhost:8080';
if (!key) {
  console.error('Cần API key của ESP32 (xem log khi backend khởi động lần đầu, hoặc Web > Thiết bị & camera > API key)');
  process.exit(1);
}

let t = 25, h = 88, c = 700;
const drift = (v, d, lo, hi) => Math.min(hi, Math.max(lo, v + (Math.random() - 0.5) * d));

async function tick() {
  t = drift(t, 2, 15, 35); h = drift(h, 6, 60, 100); c = drift(c, 300, 400, 1800);
  const body = { temperature: +t.toFixed(1), humidity: +h.toFixed(1), co2: Math.round(c) };
  try {
    const res = await fetch(`${base}/api/iot/readings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-device-key': key },
      body: JSON.stringify(body),
    });
    const json = await res.json();
    if (!res.ok) return console.log(new Date().toLocaleTimeString(), body, '->', res.status, json.error);
    const cmds = (json.commands || []).map((x) => `${x.kind}=${x.state ? 'BẬT' : 'tắt'}(${x.mode})`).join(' ');
    console.log(new Date().toLocaleTimeString(), body, '->', res.status, 'cảnh báo:', json.alerts, '| lệnh:', cmds);
  } catch (e) {
    console.error('Lỗi gửi:', e.message);
  }
}
tick();
setInterval(tick, every);
