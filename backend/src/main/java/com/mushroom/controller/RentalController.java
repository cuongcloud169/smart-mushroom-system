package com.mushroom.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mushroom.common.Db;
import com.mushroom.security.AuthUser;
import com.mushroom.security.CurrentUser;
import com.mushroom.service.RentalService;

/** Cho thuê khay: thuê, bàn giao, nhập mã khay, duyệt, hủy/hoàn tất, lịch sử giao dịch. */
@RestController
@RequestMapping("/api/rentals")
public class RentalController {
    private static final String SELECT = """
            SELECT rt.*, t.code AS tray_code, rm.name AS room_name, p.name AS package_name, u.full_name AS customer_name
            FROM rentals rt JOIN trays t ON t.id = rt.tray_id JOIN rooms rm ON rm.id = t.room_id
            LEFT JOIN rental_packages p ON p.id = rt.package_id LEFT JOIN users u ON u.id = rt.customer_id""";

    private final RentalService rentals;
    private final Db db;

    public RentalController(RentalService rentals, Db db) {
        this.rentals = rentals;
        this.db = db;
    }

    /** Nhân viên xem tất cả; khách xem lịch sử thuê của mình */
    @GetMapping
    public List<Map<String, Object>> list() {
        AuthUser u = CurrentUser.get();
        return u.isCustomer()
                ? db.list(SELECT + " WHERE rt.customer_id = ? ORDER BY rt.created_at DESC", u.id())
                : db.list(SELECT + " ORDER BY (rt.status = 'pending') DESC, rt.created_at DESC");
    }

    /** Khách thuê 1 hoặc nhiều khay: { "tray_ids": [1,2], "package_id": 1 } -> đơn chờ duyệt */
    @PostMapping
    public ResponseEntity<List<Map<String, Object>>> rent(@RequestBody Map<String, Object> b) {
        AuthUser u = CurrentUser.require("customer");
        List<Long> trayIds = b.get("tray_ids") instanceof List<?> l ? l.stream().map(Db::toLong).toList() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(rentals.rent(u.id(), trayIds, Db.toLong(b.get("package_id"))));
    }

    /** Nhân viên bàn giao khay: { tray_id, package_id, customer_id? } (bỏ trống customer_id -> khách nhập mã khay) */
    @PostMapping("/assign")
    public ResponseEntity<Map<String, Object>> assign(@RequestBody Map<String, Object> b) {
        CurrentUser.requireStaff();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                rentals.assign(Db.toLong(b.get("customer_id")), Db.toLong(b.get("tray_id")), Db.toLong(b.get("package_id"))));
    }

    /** Khách đăng ký khay bằng mã khay: { "code": "TRAY-1-001" } */
    @PostMapping("/claim")
    public Map<String, Object> claim(@RequestBody Map<String, Object> b) {
        AuthUser u = CurrentUser.require("customer");
        return rentals.claim(u.id(), Db.str(b.get("code")));
    }

    @PutMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id) {
        CurrentUser.requireStaff();
        return rentals.approve(id);
    }

    /** { "status": "completed" | "cancelled" } */
    @PutMapping("/{id}/status")
    public Map<String, Object> close(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return rentals.close(id, Db.str(b.get("status")), CurrentUser.get());
    }

    @GetMapping("/transactions")
    public List<Map<String, Object>> transactions() {
        AuthUser u = CurrentUser.get();
        String sql = """
                SELECT tr.*, u.full_name AS customer_name, t.code AS tray_code FROM transactions tr
                LEFT JOIN users u ON u.id = tr.customer_id LEFT JOIN rentals r ON r.id = tr.rental_id
                LEFT JOIN trays t ON t.id = r.tray_id""";
        return u.isCustomer()
                ? db.list(sql + " WHERE tr.customer_id = ? ORDER BY tr.created_at DESC", u.id())
                : db.list(sql + " ORDER BY tr.created_at DESC");
    }
}
