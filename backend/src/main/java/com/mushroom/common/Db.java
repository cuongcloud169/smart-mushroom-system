package com.mushroom.common;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tiện ích gọi SQL bằng JdbcTemplate cho các truy vấn có JOIN, thống kê, báo cáo.
 * Luôn dùng tham số "?" để chống SQL injection.
 * Kết quả là Map với key là tên cột (snake_case), Jackson trả thẳng ra JSON.
 */
@Component
public class Db {
    private final JdbcTemplate jdbc;

    public Db(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    /** Trả về 1 dòng hoặc null nếu không có. */
    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    public List<Long> ids(String sql, Object... args) {
        return jdbc.queryForList(sql, Long.class, args);
    }

    public static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    public static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        String s = o.toString().trim();
        return s.isEmpty() ? null : Double.valueOf(s);
    }

    public static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
