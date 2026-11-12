package com.mushroom.controller;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mushroom.entity.RentalPackage;
import com.mushroom.repository.RentalPackageRepository;
import com.mushroom.security.AuthUser;
import com.mushroom.security.CurrentUser;

import jakarta.validation.Validator;

/** Gói thuê và giá: mọi người xem (khách chỉ thấy gói đang bán), chỉ Admin thêm/sửa/xóa. */
@RestController
@RequestMapping("/api/packages")
public class PackageController extends CrudController<RentalPackage> {
    public PackageController(RentalPackageRepository repo, ObjectMapper mapper, Validator validator) {
        super(repo, mapper, validator, RentalPackage.class);
    }

    @Override protected String[] readRoles() { return new String[] {"admin", "operator", "customer"}; }
    @Override protected String[] writeRoles() { return new String[] {"admin"}; }

    @Override
    @GetMapping
    public List<RentalPackage> list() {
        AuthUser u = CurrentUser.require(readRoles());
        List<RentalPackage> all = repo.findAll(Sort.by("price"));
        return u.isCustomer() ? all.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList() : all;
    }
}
