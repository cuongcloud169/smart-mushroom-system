package com.mushroom.controller;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mushroom.common.ApiException;
import com.mushroom.entity.User;
import com.mushroom.repository.UserRepository;
import com.mushroom.security.AuthUser;
import com.mushroom.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Quản lý tài khoản (chỉ Admin): tạo, sửa, phân quyền, vô hiệu hóa, xóa, đặt lại mật khẩu. */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final String ROLES = "admin|operator|customer";

    public record CreateUserRequest(
            @NotBlank @Email(message = "Email không hợp lệ") String email,
            @NotBlank @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự") String password,
            @NotBlank(message = "Cần họ tên") String fullName,
            @Pattern(regexp = AuthController.PHONE, message = "Số điện thoại không hợp lệ") String phone,
            String address,
            @NotBlank @Pattern(regexp = ROLES, message = "Vai trò phải là admin, operator hoặc customer") String role) {}

    public record UpdateUserRequest(
            String fullName,
            @Pattern(regexp = AuthController.PHONE, message = "Số điện thoại không hợp lệ") String phone,
            String address,
            @Pattern(regexp = ROLES, message = "Vai trò phải là admin, operator hoặc customer") String role,
            Boolean isActive) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<User> list(@RequestParam(required = false) String role) {
        CurrentUser.requireAdmin();
        return role == null || role.isBlank() ? users.findAll(org.springframework.data.domain.Sort.by("id"))
                : users.findByRoleOrderById(role);
    }

    /** Danh sách khách hàng đang hoạt động (cho Operator chọn khi bàn giao khay) */
    @GetMapping("/customers")
    public List<User> customers() {
        CurrentUser.requireStaff();
        return users.findByRoleOrderById("customer").stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).toList();
    }

    @PostMapping
    public ResponseEntity<User> create(@Valid @RequestBody CreateUserRequest r) {
        CurrentUser.requireAdmin();
        String email = r.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) throw ApiException.conflict("Email đã tồn tại");
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setPhone(AuthController.blankToNull(r.phone()));
        u.setAddress(AuthController.blankToNull(r.address()));
        u.setRole(r.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(users.save(u));
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
        AuthUser me = CurrentUser.requireAdmin();
        User u = find(id);
        boolean self = id.equals(me.id());
        if (self && r.role() != null && !"admin".equals(r.role())) throw ApiException.badRequest("Không thể tự hạ quyền của mình");
        if (self && Boolean.FALSE.equals(r.isActive())) throw ApiException.badRequest("Không thể tự vô hiệu hóa tài khoản đang đăng nhập");
        if (r.fullName() != null && !r.fullName().isBlank()) u.setFullName(r.fullName().trim());
        if (r.phone() != null) u.setPhone(AuthController.blankToNull(r.phone()));
        if (r.address() != null) u.setAddress(AuthController.blankToNull(r.address()));
        if (r.role() != null && !r.role().isBlank()) u.setRole(r.role());
        if (r.isActive() != null) u.setIsActive(r.isActive());
        return users.save(u);
    }

    /** Đặt lại mật khẩu: dùng mật khẩu gửi lên hoặc sinh ngẫu nhiên 10 ký tự */
    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        CurrentUser.requireAdmin();
        User u = find(id);
        String pw = body != null && body.get("new_password") != null && !body.get("new_password").isBlank()
                ? body.get("new_password") : randomPassword();
        if (pw.length() < 8) throw ApiException.badRequest("Mật khẩu tối thiểu 8 ký tự");
        u.setPasswordHash(encoder.encode(pw));
        users.save(u);
        return Map.of("ok", true, "new_password", pw);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        AuthUser me = CurrentUser.requireAdmin();
        if (id.equals(me.id())) throw ApiException.badRequest("Không thể tự xóa tài khoản đang đăng nhập");
        users.delete(find(id));
        return Map.of("ok", true);
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    private String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.append("@1").toString();
    }
}
