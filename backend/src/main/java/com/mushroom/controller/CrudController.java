package com.mushroom.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mushroom.common.ApiException;
import com.mushroom.security.CurrentUser;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Lớp cha tạo sẵn 5 API chuẩn cho một bảng: xem danh sách, xem 1, thêm, sửa, xóa.
 * Các lớp con (Room, Tray, Package, Device, Threshold) chỉ khai báo quyền và kiểm tra riêng.
 * Dữ liệu gửi lên dạng snake_case (vd room_id) được Jackson ánh xạ vào Entity.
 */
public abstract class CrudController<T> {
    private static final Set<String> READ_ONLY = Set.of("id", "created_at");

    protected final JpaRepository<T, Long> repo;
    protected final ObjectMapper mapper;
    protected final Validator validator;
    private final Class<T> type;

    protected CrudController(JpaRepository<T, Long> repo, ObjectMapper mapper, Validator validator, Class<T> type) {
        this.repo = repo;
        this.mapper = mapper;
        this.validator = validator;
        this.type = type;
    }

    /** Vai trò được xem / thêm-sửa / xóa */
    protected String[] readRoles() { return new String[] {"admin", "operator"}; }
    protected String[] writeRoles() { return new String[] {"admin", "operator"}; }
    protected String[] deleteRoles() { return new String[] {"admin"}; }
    protected Sort sort() { return Sort.by("id"); }

    /** Kiểm tra / bổ sung dữ liệu trước khi lưu (lớp con ghi đè nếu cần). */
    protected void beforeSave(T entity, boolean isNew) {}

    @GetMapping
    public List<T> list() {
        CurrentUser.require(readRoles());
        return repo.findAll(sort());
    }

    @GetMapping("/{id}")
    public T get(@PathVariable Long id) {
        CurrentUser.require(readRoles());
        return find(id);
    }

    @PostMapping
    public ResponseEntity<T> create(@RequestBody Map<String, Object> body) {
        CurrentUser.require(writeRoles());
        T e = mapper.convertValue(clean(body), type);
        beforeSave(e, true);
        validate(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(e));
    }

    @PutMapping("/{id}")
    public T update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CurrentUser.require(writeRoles());
        T e = find(id);
        try {
            mapper.updateValue(e, clean(body));
        } catch (JsonMappingException ex) {
            throw ApiException.badRequest("Sai định dạng dữ liệu");
        }
        beforeSave(e, false);
        validate(e);
        return repo.save(e);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        CurrentUser.require(deleteRoles());
        T e = find(id);
        repo.delete(e); // lỗi khóa ngoại (đang được dùng) được GlobalExceptionHandler trả về 409
        return Map.of("ok", true);
    }

    protected T find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy"));
    }

    /** Bỏ các trường không được sửa và đổi chuỗi rỗng thành null. */
    private Map<String, Object> clean(Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>();
        if (body == null) return m;
        body.forEach((k, v) -> {
            if (READ_ONLY.contains(k)) return;
            m.put(k, (v instanceof String s && s.isBlank()) ? null : v);
        });
        return m;
    }

    protected void validate(T e) {
        Set<ConstraintViolation<T>> v = validator.validate(e);
        if (!v.isEmpty()) throw ApiException.badRequest(v.iterator().next().getMessage());
    }
}
