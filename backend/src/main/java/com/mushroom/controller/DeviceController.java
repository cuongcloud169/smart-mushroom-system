package com.mushroom.controller;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mushroom.common.ApiException;
import com.mushroom.entity.Device;
import com.mushroom.repository.DeviceRepository;
import com.mushroom.security.CurrentUser;

import jakarta.validation.Validator;

/**
 * Quản lý thiết bị IoT, cảm biến và camera.
 * Admin và Farm Operator thêm/sửa (Operator quản lý camera theo yêu cầu đề), chỉ Admin xóa
 * và xem/cấp lại API key của ESP32.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController extends CrudController<Device> {
    private static final SecureRandom RANDOM = new SecureRandom();

    public DeviceController(DeviceRepository repo, ObjectMapper mapper, Validator validator) {
        super(repo, mapper, validator, Device.class);
    }

    @Override
    protected void beforeSave(Device d, boolean isNew) {
        if (isNew) d.setApiKey(newKey());
        if ("actuator".equals(d.getType()) && d.getActuatorKind() == null)
            throw ApiException.badRequest("Thiết bị điều khiển cần chọn loại: mist, fan, light hoặc heater");
        if (!"actuator".equals(d.getType())) d.setActuatorKind(null);
        if ("camera".equals(d.getType()) && (d.getStreamUrl() == null || d.getStreamUrl().isBlank()))
            throw ApiException.badRequest("Camera cần có đường dẫn xem hình (stream_url)");
    }

    @GetMapping("/{id}/api-key")
    public Map<String, Object> apiKey(@PathVariable Long id) {
        CurrentUser.requireAdmin();
        return Map.of("id", id, "api_key", find(id).getApiKey());
    }

    @PostMapping("/{id}/regenerate-key")
    public Map<String, Object> regenerate(@PathVariable Long id) {
        CurrentUser.requireAdmin();
        Device d = find(id);
        d.setApiKey(newKey());
        repo.save(d);
        return Map.of("id", id, "api_key", d.getApiKey());
    }

    private static String newKey() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
