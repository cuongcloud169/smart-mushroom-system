package com.mushroom.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mushroom.common.ApiException;
import com.mushroom.common.Db;
import com.mushroom.security.AuthUser;

/**
 * Nghiệp vụ cho thuê khay.
 * Vòng đời hợp đồng: pending (khách gửi đơn) -> active (nhân viên duyệt) -> completed (hết hạn/hoàn tất) | cancelled.
 * Giao dịch: pending khi gửi đơn -> paid khi duyệt -> refunded khi hủy sau khi đã thanh toán (failed nếu hủy lúc chờ duyệt).
 */
@Service
public class RentalService {
    private final Db db;
    private final NotificationService notifications;

    public RentalService(Db db, NotificationService notifications) {
        this.db = db;
        this.notifications = notifications;
    }

    /**
     * Tạo hợp đồng cho 1 khay. Khóa dòng khay (FOR UPDATE) để hai người không thuê trùng.
     * @param customerId null = bàn giao khay, chờ khách nhập mã khay
     * @param status     pending (khách tự thuê) hoặc active (nhân viên bàn giao)
     */
    private Map<String, Object> createRental(Long customerId, Long trayId, Long packageId, String status) {
        Map<String, Object> tray = db.one("""
                SELECT t.*, r.status AS room_status FROM trays t JOIN rooms r ON r.id = t.room_id
                WHERE t.id = ? FOR UPDATE OF t""", trayId);
        if (tray == null) throw ApiException.notFound("Không tìm thấy khay #" + trayId);
        if (!"empty".equals(tray.get("status"))) throw ApiException.conflict("Khay " + tray.get("code") + " không còn trống");
        if (!"active".equals(tray.get("room_status"))) throw ApiException.conflict("Phòng của khay " + tray.get("code") + " đang không hoạt động");
        Map<String, Object> pkg = db.one("SELECT * FROM rental_packages WHERE id = ? AND is_active", packageId);
        if (pkg == null) throw ApiException.badRequest("Gói thuê không hợp lệ hoặc đã ngừng bán");

        Map<String, Object> rental = db.one("""
                INSERT INTO rentals (customer_id, tray_id, package_id, end_date, status, total_price, approved_at)
                VALUES (?, ?, ?, CURRENT_DATE + ?::int, ?, ?, CASE WHEN ? = 'active' THEN now() END) RETURNING *""",
                customerId, trayId, packageId, pkg.get("duration_days"), status, pkg.get("price"), status);
        db.update("UPDATE trays SET status = 'rented' WHERE id = ?", trayId);
        if (customerId != null) {
            db.update("INSERT INTO transactions (rental_id, customer_id, amount, status) VALUES (?, ?, ?, ?)",
                    rental.get("id"), customerId, pkg.get("price"), "active".equals(status) ? "paid" : "pending");
        }
        rental.put("tray_code", tray.get("code"));
        return rental;
    }

    /** Khách thuê 1 hoặc nhiều khay: tạo đơn chờ duyệt. Lỗi ở 1 khay -> hủy toàn bộ (transaction). */
    @Transactional
    public List<Map<String, Object>> rent(Long customerId, List<Long> trayIds, Long packageId) {
        if (trayIds == null || trayIds.isEmpty() || packageId == null) throw ApiException.badRequest("Cần tray_ids (mảng) và package_id");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : trayIds) out.add(createRental(customerId, id, packageId, "pending"));
        notifications.notifyStaff("rental", "Đơn thuê khay mới chờ duyệt",
                "Khách #" + customerId + " đăng ký thuê " + out.stream().map(r -> String.valueOf(r.get("tray_code"))).toList());
        return out;
    }

    /** Nhân viên bàn giao khay. customerId null -> khách tự nhập mã khay để nhận. */
    @Transactional
    public Map<String, Object> assign(Long customerId, Long trayId, Long packageId) {
        if (trayId == null || packageId == null) throw ApiException.badRequest("Cần tray_id và package_id");
        if (customerId != null && db.one("SELECT 1 FROM users WHERE id = ? AND role = 'customer' AND is_active", customerId) == null) {
            throw ApiException.badRequest("Khách hàng không tồn tại hoặc đã bị vô hiệu hóa");
        }
        Map<String, Object> r = createRental(customerId, trayId, packageId, "active");
        if (customerId != null) {
            notifications.notifyCustomers("rental", List.of(customerId), "Khay đã được bàn giao",
                    "Khay " + r.get("tray_code") + " đã được gán cho bạn");
        }
        return r;
    }

    /** Khách nhập mã khay duy nhất để liên kết khay (đã được bàn giao) với tài khoản. */
    @Transactional
    public Map<String, Object> claim(Long customerId, String code) {
        if (code == null || code.isBlank()) throw ApiException.badRequest("Cần nhập mã khay");
        Map<String, Object> r = db.one("""
                UPDATE rentals SET customer_id = ? WHERE status = 'active' AND customer_id IS NULL
                AND tray_id = (SELECT id FROM trays WHERE upper(code) = upper(?)) RETURNING *""", customerId, code.trim());
        if (r == null) throw ApiException.notFound("Mã khay không hợp lệ hoặc khay đã có chủ");
        db.update("INSERT INTO transactions (rental_id, customer_id, amount, status) VALUES (?, ?, ?, 'paid')",
                r.get("id"), customerId, r.get("total_price"));
        return r;
    }

    /** Nhân viên duyệt đơn: pending -> active, thời hạn tính lại từ ngày duyệt. */
    @Transactional
    public Map<String, Object> approve(Long rentalId) {
        Map<String, Object> r = db.one("""
                UPDATE rentals r SET status = 'active', approved_at = now(), start_date = CURRENT_DATE,
                       end_date = CURRENT_DATE + COALESCE((SELECT duration_days FROM rental_packages p WHERE p.id = r.package_id), 30)
                WHERE id = ? AND status = 'pending' RETURNING *""", rentalId);
        if (r == null) throw ApiException.notFound("Không tìm thấy đơn đang chờ duyệt");
        db.update("UPDATE transactions SET status = 'paid' WHERE rental_id = ? AND status = 'pending'", rentalId);
        notifications.notifyCustomers("rental", List.of(Db.toLong(r.get("customer_id"))), "Đơn thuê đã được duyệt",
                "Đơn thuê #" + rentalId + " đã được duyệt, hạn thuê đến " + r.get("end_date"));
        return r;
    }

    /**
     * Kết thúc hợp đồng: completed (chỉ nhân viên) hoặc cancelled (nhân viên, hoặc khách hủy đơn của mình).
     * Khay trả về trạng thái trống; tiền đã trả được đánh dấu hoàn (refunded) khi hủy.
     */
    @Transactional
    public Map<String, Object> close(Long rentalId, String status, AuthUser user) {
        if (!"completed".equals(status) && !"cancelled".equals(status)) {
            throw ApiException.badRequest("status phải là completed hoặc cancelled");
        }
        boolean own = user.isCustomer();
        if (own && !"cancelled".equals(status)) throw ApiException.forbidden("Khách hàng chỉ được hủy hợp đồng");
        Map<String, Object> r = db.one("""
                UPDATE rentals SET status = ? WHERE id = ? AND status IN ('pending','active')
                AND (?::bigint IS NULL OR customer_id = ?::bigint) RETURNING *""",
                status, rentalId, own ? user.id() : null, own ? user.id() : null);
        if (r == null) throw ApiException.notFound("Không tìm thấy hợp đồng đang mở");
        releaseTray(Db.toLong(r.get("tray_id")));
        if ("cancelled".equals(status)) {
            db.update("UPDATE transactions SET status = 'refunded' WHERE rental_id = ? AND status = 'paid'", rentalId);
            db.update("UPDATE transactions SET status = 'failed' WHERE rental_id = ? AND status = 'pending'", rentalId);
        }
        return r;
    }

    private void releaseTray(Long trayId) {
        db.update("""
                UPDATE trays SET status = 'empty', growth_stage = NULL, fill_percent = 0, expected_harvest_date = NULL
                WHERE id = ?""", trayId);
    }

    /**
     * Chạy mỗi giờ:
     * - Nhắc khách hợp đồng còn 3 ngày (mỗi hợp đồng nhắc 1 lần).
     * - Hợp đồng quá ngày kết thúc -> completed, trả khay về trống, báo cho khách và nhân viên.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 30_000)
    @Transactional
    public void processExpiredRentals() {
        List<Map<String, Object>> soon = db.list("""
                UPDATE rentals SET reminder_sent = TRUE
                WHERE status = 'active' AND NOT reminder_sent AND customer_id IS NOT NULL
                  AND end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 3
                RETURNING id, customer_id, end_date""");
        for (Map<String, Object> r : soon) {
            notifications.notifyCustomers("rental_expiry", List.of(Db.toLong(r.get("customer_id"))),
                    "Hợp đồng sắp hết hạn", "Hợp đồng #" + r.get("id") + " hết hạn ngày " + r.get("end_date"));
        }
        List<Map<String, Object>> expired = db.list("""
                UPDATE rentals SET status = 'completed'
                WHERE status = 'active' AND end_date < CURRENT_DATE
                RETURNING id, tray_id, customer_id""");
        for (Map<String, Object> r : expired) {
            releaseTray(Db.toLong(r.get("tray_id")));
            Long cid = Db.toLong(r.get("customer_id"));
            notifications.notifyEvent("rental_expiry", cid == null ? List.of() : List.of(cid),
                    "Hợp đồng đã hết hạn", "Hợp đồng #" + r.get("id") + " đã kết thúc, khay được trả về trạng thái trống");
        }
        // Đơn chờ duyệt quá 7 ngày: tự hủy để không giữ khay
        List<Map<String, Object>> stale = db.list("""
                UPDATE rentals SET status = 'cancelled'
                WHERE status = 'pending' AND created_at < now() - interval '7 days'
                RETURNING id, tray_id""");
        for (Map<String, Object> r : stale) {
            releaseTray(Db.toLong(r.get("tray_id")));
            db.update("UPDATE transactions SET status = 'failed' WHERE rental_id = ? AND status = 'pending'", r.get("id"));
        }
    }
}
