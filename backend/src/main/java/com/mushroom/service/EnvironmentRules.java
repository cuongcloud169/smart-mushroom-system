package com.mushroom.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Quy tắc môi trường (thuần Java, không phụ thuộc CSDL nên dễ viết unit test):
 * 1) Kiểm tra số đo hợp lệ.
 * 2) Phát hiện vượt ngưỡng để tạo cảnh báo.
 * 3) Quyết định bật/tắt thiết bị ở chế độ tự động.
 */
public final class EnvironmentRules {
    private EnvironmentRules() {}

    public record Thresholds(double tempMin, double tempMax, double humMin, double humMax, double co2Max,
                             int lightOnHour, int lightOffHour) {}

    public record Issue(String type, String message, double value) {}

    /** Trả về thông báo lỗi nếu số đo nằm ngoài khoảng vật lý hợp lý của cảm biến, null nếu hợp lệ. */
    public static String validate(Double t, Double h, Double c) {
        if (t == null && h == null && c == null) return "Thiếu dữ liệu cảm biến";
        if (t != null && (t < -10 || t > 60)) return "Nhiệt độ ngoài khoảng hợp lệ (-10..60 °C)";
        if (h != null && (h < 0 || h > 100)) return "Độ ẩm ngoài khoảng hợp lệ (0..100 %)";
        if (c != null && (c < 0 || c > 10000)) return "CO2 ngoài khoảng hợp lệ (0..10000 ppm)";
        return null;
    }

    public static List<Issue> checkIssues(Double t, Double h, Double c, Thresholds th) {
        List<Issue> out = new ArrayList<>();
        if (t != null && t > th.tempMax()) out.add(new Issue("temp_high", "Nhiệt độ " + t + "°C vượt ngưỡng " + th.tempMax() + "°C", t));
        if (t != null && t < th.tempMin()) out.add(new Issue("temp_low", "Nhiệt độ " + t + "°C thấp hơn ngưỡng " + th.tempMin() + "°C", t));
        if (h != null && h > th.humMax()) out.add(new Issue("hum_high", "Độ ẩm " + h + "% vượt ngưỡng " + th.humMax() + "%", h));
        if (h != null && h < th.humMin()) out.add(new Issue("hum_low", "Độ ẩm " + h + "% thấp hơn ngưỡng " + th.humMin() + "%", h));
        if (c != null && c > th.co2Max()) out.add(new Issue("co2_high", "CO2 " + c + "ppm vượt ngưỡng " + th.co2Max() + "ppm", c));
        return out;
    }

    /**
     * Trạng thái mới của thiết bị ở chế độ auto.
     * - mist (phun sương): bật khi độ ẩm < min, tắt khi đạt max
     * - fan (quạt): bật khi CO2 > max hoặc nhiệt độ > max; tắt khi CO2 <= 90% max và nhiệt độ <= max
     * - heater (sưởi): bật khi nhiệt độ < min, tắt khi >= min + 1
     * - light (đèn LED): bật trong khung giờ [lightOnHour, lightOffHour)
     * Thiếu số đo tương ứng thì giữ nguyên trạng thái.
     */
    public static boolean nextState(String kind, boolean current, Double t, Double h, Double c,
                                    Thresholds th, int hourNow) {
        switch (kind) {
            case "mist":
                if (h == null) return current;
                if (h < th.humMin()) return true;
                if (h >= th.humMax()) return false;
                return current;
            case "fan":
                boolean hot = t != null && t > th.tempMax();
                boolean co2High = c != null && c > th.co2Max();
                if (hot || co2High) return true;
                boolean co2Ok = c == null || c <= th.co2Max() * 0.9;
                boolean tempOk = t == null || t <= th.tempMax();
                if (co2Ok && tempOk && (t != null || c != null)) return false;
                return current;
            case "heater":
                if (t == null) return current;
                if (t < th.tempMin()) return true;
                if (t >= th.tempMin() + 1) return false;
                return current;
            case "light":
                return inWindow(hourNow, th.lightOnHour(), th.lightOffHour());
            default:
                return current;
        }
    }

    /** Giờ hiện tại có nằm trong khung [on, off) không; hỗ trợ khung qua nửa đêm (vd 20h -> 6h). */
    static boolean inWindow(int hour, int on, int off) {
        if (on == off) return false;
        return on < off ? hour >= on && hour < off : hour >= on || hour < off;
    }
}
