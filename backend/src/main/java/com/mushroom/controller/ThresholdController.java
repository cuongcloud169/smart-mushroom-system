package com.mushroom.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mushroom.common.ApiException;
import com.mushroom.entity.Threshold;
import com.mushroom.repository.ThresholdRepository;

import jakarta.validation.Validator;

/** Cấu hình ngưỡng môi trường (room_id rỗng = ngưỡng mặc định). Chỉ Admin sửa. */
@RestController
@RequestMapping("/api/thresholds")
public class ThresholdController extends CrudController<Threshold> {
    public ThresholdController(ThresholdRepository repo, ObjectMapper mapper, Validator validator) {
        super(repo, mapper, validator, Threshold.class);
    }

    @Override protected String[] writeRoles() { return new String[] {"admin"}; }

    @Override
    protected void beforeSave(Threshold t, boolean isNew) {
        if (t.getTempMin() != null && t.getTempMax() != null && t.getTempMin().compareTo(t.getTempMax()) >= 0)
            throw ApiException.badRequest("Nhiệt độ tối thiểu phải nhỏ hơn tối đa");
        if (t.getHumMin() != null && t.getHumMax() != null && t.getHumMin().compareTo(t.getHumMax()) >= 0)
            throw ApiException.badRequest("Độ ẩm tối thiểu phải nhỏ hơn tối đa");
    }
}
