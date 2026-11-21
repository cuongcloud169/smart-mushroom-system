-- =====================================================================
-- Hệ thống Quản lý Nuôi trồng Nấm Thông minh - PostgreSQL schema (Flyway V1)
-- =====================================================================

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  full_name     TEXT NOT NULL,
  phone         TEXT,
  address       TEXT,
  role          TEXT NOT NULL CHECK (role IN ('admin','operator','customer')),
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rooms (
  id          BIGSERIAL PRIMARY KEY,
  name        TEXT UNIQUE NOT NULL,
  description TEXT,
  capacity    INT NOT NULL DEFAULT 0 CHECK (capacity >= 0),
  status      TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','maintenance','inactive')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Mỗi khay có mã định danh duy nhất (code) để khách đăng ký khay
CREATE TABLE trays (
  id                    BIGSERIAL PRIMARY KEY,
  code                  TEXT UNIQUE NOT NULL,
  room_id               BIGINT NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
  status                TEXT NOT NULL DEFAULT 'empty'
                        CHECK (status IN ('empty','rented','growing','ready_harvest','harvested','maintenance')),
  fill_percent          INT NOT NULL DEFAULT 0 CHECK (fill_percent BETWEEN 0 AND 100),
  mushroom_type         TEXT,
  growth_stage          TEXT,
  expected_harvest_date DATE,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tray_progress_logs (
  id           BIGSERIAL PRIMARY KEY,
  tray_id      BIGINT NOT NULL REFERENCES trays(id) ON DELETE CASCADE,
  operator_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
  growth_stage TEXT,
  status       TEXT,
  note         TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rental_packages (
  id            BIGSERIAL PRIMARY KEY,
  name          TEXT NOT NULL,
  description   TEXT,
  duration_days INT NOT NULL CHECK (duration_days > 0),
  price         NUMERIC(12,2) NOT NULL CHECK (price >= 0),
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Vòng đời hợp đồng: pending (chờ duyệt) -> active -> completed | cancelled
-- customer_id NULL = khay đã bàn giao, chờ khách đăng ký bằng mã khay
CREATE TABLE rentals (
  id            BIGSERIAL PRIMARY KEY,
  customer_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
  tray_id       BIGINT NOT NULL REFERENCES trays(id) ON DELETE RESTRICT,
  package_id    BIGINT REFERENCES rental_packages(id) ON DELETE SET NULL,
  start_date    DATE NOT NULL DEFAULT CURRENT_DATE,
  end_date      DATE,
  status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','active','completed','cancelled')),
  total_price   NUMERIC(12,2) NOT NULL DEFAULT 0,
  approved_at   TIMESTAMPTZ,
  reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Mỗi khay chỉ có tối đa 1 hợp đồng đang chờ duyệt hoặc đang hoạt động
CREATE UNIQUE INDEX one_open_rental_per_tray ON rentals(tray_id) WHERE status IN ('pending','active');

CREATE TABLE transactions (
  id          BIGSERIAL PRIMARY KEY,
  rental_id   BIGINT REFERENCES rentals(id) ON DELETE SET NULL,
  customer_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  amount      NUMERIC(12,2) NOT NULL,
  method      TEXT NOT NULL DEFAULT 'cash',
  status      TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','paid','refunded','failed')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
  id            BIGSERIAL PRIMARY KEY,
  room_id       BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  tray_id       BIGINT REFERENCES trays(id) ON DELETE SET NULL,
  name          TEXT NOT NULL,
  type          TEXT NOT NULL CHECK (type IN ('controller','sensor','camera','actuator')),
  actuator_kind TEXT CHECK (actuator_kind IN ('mist','fan','light','heater')),
  stream_url    TEXT,
  api_key       TEXT UNIQUE NOT NULL,
  state         BOOLEAN NOT NULL DEFAULT FALSE,
  mode          TEXT NOT NULL DEFAULT 'auto' CHECK (mode IN ('auto','manual')),
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  last_seen     TIMESTAMPTZ,
  offline_notified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (type <> 'actuator' OR actuator_kind IS NOT NULL)
);

CREATE TABLE sensor_readings (
  id          BIGSERIAL PRIMARY KEY,
  room_id     BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  device_id   BIGINT REFERENCES devices(id) ON DELETE SET NULL,
  temperature NUMERIC(5,2),
  humidity    NUMERIC(5,2),
  co2         NUMERIC(8,2),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_readings_room_time ON sensor_readings(room_id, recorded_at DESC);

-- room_id NULL = ngưỡng mặc định cho mọi phòng chưa cấu hình riêng
CREATE TABLE thresholds (
  id             BIGSERIAL PRIMARY KEY,
  room_id        BIGINT UNIQUE REFERENCES rooms(id) ON DELETE CASCADE,
  temp_min       NUMERIC(5,2) NOT NULL DEFAULT 20,
  temp_max       NUMERIC(5,2) NOT NULL DEFAULT 28,
  hum_min        NUMERIC(5,2) NOT NULL DEFAULT 80,
  hum_max        NUMERIC(5,2) NOT NULL DEFAULT 95,
  co2_max        NUMERIC(8,2) NOT NULL DEFAULT 1000,
  light_on_hour  INT NOT NULL DEFAULT 6  CHECK (light_on_hour BETWEEN 0 AND 23),
  light_off_hour INT NOT NULL DEFAULT 18 CHECK (light_off_hour BETWEEN 0 AND 23),
  CHECK (temp_min < temp_max AND hum_min < hum_max)
);
CREATE UNIQUE INDEX one_default_threshold ON thresholds((room_id IS NULL)) WHERE room_id IS NULL;

CREATE TABLE alerts (
  id         BIGSERIAL PRIMARY KEY,
  room_id    BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  type       TEXT NOT NULL,
  message    TEXT NOT NULL,
  value      NUMERIC(8,2),
  resolved   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title      TEXT NOT NULL,
  body       TEXT,
  type       TEXT NOT NULL DEFAULT 'info',
  is_read    BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_user ON notifications(user_id, is_read, created_at DESC);

-- Cài đặt thông báo: loại sự kiện nào được gửi, gửi cho nhân viên và/hoặc khách hàng
CREATE TABLE notification_settings (
  event_type      TEXT PRIMARY KEY,
  label           TEXT NOT NULL,
  enabled         BOOLEAN NOT NULL DEFAULT TRUE,
  notify_staff    BOOLEAN NOT NULL DEFAULT TRUE,
  notify_customer BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE harvest_schedules (
  id           BIGSERIAL PRIMARY KEY,
  tray_id      BIGINT NOT NULL REFERENCES trays(id) ON DELETE CASCADE,
  planned_date DATE NOT NULL,
  status       TEXT NOT NULL DEFAULT 'planned' CHECK (status IN ('planned','harvested','cancelled')),
  yield_kg     NUMERIC(8,2) CHECK (yield_kg >= 0),
  note         TEXT,
  harvested_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE delivery_requests (
  id          BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tray_id     BIGINT REFERENCES trays(id) ON DELETE SET NULL,
  type        TEXT NOT NULL CHECK (type IN ('pickup','delivery')),
  address     TEXT,
  note        TEXT,
  status      TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','confirmed','delivering','done','cancelled')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (type <> 'delivery' OR address IS NOT NULL)
);
