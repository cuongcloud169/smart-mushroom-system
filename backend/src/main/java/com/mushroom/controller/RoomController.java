package com.mushroom.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mushroom.entity.Room;
import com.mushroom.repository.RoomRepository;

import jakarta.validation.Validator;

/** Quản lý phòng nuôi: Admin và Operator thêm/xem/sửa, chỉ Admin xóa. */
@RestController
@RequestMapping("/api/rooms")
public class RoomController extends CrudController<Room> {
    public RoomController(RoomRepository repo, ObjectMapper mapper, Validator validator) {
        super(repo, mapper, validator, Room.class);
    }
}
