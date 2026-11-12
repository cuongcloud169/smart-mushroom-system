package com.mushroom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mushroom.entity.RentalPackage;

public interface RentalPackageRepository extends JpaRepository<RentalPackage, Long> {

}
