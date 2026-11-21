package com.mushroom.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mushroom.common.ApiException;
import com.mushroom.entity.User;
import com.mushroom.repository.UserRepository;
import com.mushroom.security.CurrentUser;
import com.mushroom.security.JwtService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Đăng ký, đăng nhập, hồ sơ cá nhân, đổi mật khẩu. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    /** Số điện thoại Việt Nam: 0xxxxxxxxx hoặc +84xxxxxxxxx; cho phép bỏ trống */
    static final String PHONE = "^$|^(0|\\+84)\\d{9,10}$";

    public record RegisterRequest(
            @NotBlank @Email(message = "Email không hợp lệ") String email,
            @NotBlank @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự") String password,
            @NotBlank(message = "Cần họ tên") String fullName,
            @Pattern(regexp = PHONE, message = "Số điện thoại không hợp lệ") String phone,
            String address,
            String inviteCode) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record ProfileRequest(String fullName,
                                 @Pattern(regexp = PHONE, message = "Số điện thoại không hợp lệ") String phone,
                                 String address) {}

    public record PasswordRequest(@NotBlank String oldPassword,
                                  @NotBlank @Size(min = 8, message = "Mật khẩu mới tối thiểu 8 ký tự") String newPassword) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String adminInviteCode;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                          @Value("${app.admin-invite-code}") String adminInviteCode) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.adminInviteCode = adminInviteCode;
    }

    /** Khách hàng tự đăng ký (role luôn là customer) */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createAndLogin(r, "customer"));
    }

    /** Đăng ký tài khoản Administrator: bắt buộc mã mời (cấu hình app.admin-invite-code) */
    @PostMapping("/register-admin")
    public ResponseEntity<Map<String, Object>> registerAdmin(@Valid @RequestBody RegisterRequest r) {
        if (r.inviteCode() == null || !r.inviteCode().equals(adminInviteCode)) {
            throw ApiException.forbidden("Mã mời không đúng");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(createAndLogin(r, "admin"));
    }

    private Map<String, Object> createAndLogin(RegisterRequest r, String role) {
        String email = r.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) throw ApiException.conflict("Email đã được sử dụng");
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setPhone(blankToNull(r.phone()));
        u.setAddress(blankToNull(r.address()));
        u.setRole(role);
        users.save(u);
        return Map.of("user", u, "token", jwt.sign(u));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest r) {
        User u = users.findByEmailIgnoreCase(r.email().trim())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Sai email hoặc mật khẩu"));
        if (!Boolean.TRUE.equals(u.getIsActive())) throw ApiException.forbidden("Tài khoản đã bị vô hiệu hóa");
        return Map.of("user", u, "token", jwt.sign(u));
    }

    @GetMapping("/me")
    public User me() {
        return current();
    }

    @PutMapping("/me")
    public User updateMe(@Valid @RequestBody ProfileRequest r) {
        User u = current();
        if (r.fullName() != null && !r.fullName().isBlank()) u.setFullName(r.fullName().trim());
        if (r.phone() != null) u.setPhone(blankToNull(r.phone()));
        if (r.address() != null) u.setAddress(blankToNull(r.address()));
        return users.save(u);
    }

    @PutMapping("/me/password")
    public Map<String, Object> changePassword(@Valid @RequestBody PasswordRequest r) {
        User u = current();
        if (!encoder.matches(r.oldPassword(), u.getPasswordHash())) throw ApiException.badRequest("Mật khẩu cũ không đúng");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);
        return Map.of("ok", true);
    }

    private User current() {
        return users.findById(CurrentUser.get().id()).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
