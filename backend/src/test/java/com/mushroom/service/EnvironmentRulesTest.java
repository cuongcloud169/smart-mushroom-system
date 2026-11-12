package com.mushroom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mushroom.service.EnvironmentRules.Issue;
import com.mushroom.service.EnvironmentRules.Thresholds;

/** Kiểm thử quy tắc cảnh báo và tự động điều khiển (chạy: mvn test) */
class EnvironmentRulesTest {
    private final Thresholds th = new Thresholds(20, 28, 80, 95, 1000, 6, 18);

    @Test
    void validReadingsPass() {
        assertNull(EnvironmentRules.validate(25.0, 85.0, 700.0));
        assertNull(EnvironmentRules.validate(25.0, null, null));
    }

    @Test
    void invalidReadingsRejected() {
        assertNotNull(EnvironmentRules.validate(null, null, null));
        assertNotNull(EnvironmentRules.validate(-999.0, 85.0, 700.0));
        assertNotNull(EnvironmentRules.validate(25.0, 120.0, 700.0));
        assertNotNull(EnvironmentRules.validate(25.0, 85.0, -5.0));
    }

    @Test
    void noIssueWhenInRange() {
        assertTrue(EnvironmentRules.checkIssues(25.0, 85.0, 700.0, th).isEmpty());
    }

    @Test
    void detectsAllIssueTypes() {
        assertEquals("temp_high", EnvironmentRules.checkIssues(30.0, null, null, th).get(0).type());
        assertEquals("temp_low", EnvironmentRules.checkIssues(18.0, null, null, th).get(0).type());
        assertEquals("hum_low", EnvironmentRules.checkIssues(null, 70.0, null, th).get(0).type());
        assertEquals("hum_high", EnvironmentRules.checkIssues(null, 99.0, null, th).get(0).type());
        List<Issue> co2 = EnvironmentRules.checkIssues(null, null, 1500.0, th);
        assertEquals("co2_high", co2.get(0).type());
        assertEquals(1500.0, co2.get(0).value());
    }

    @Test
    void mistTurnsOnWhenDryAndOffWhenHumid() {
        assertTrue(EnvironmentRules.nextState("mist", false, null, 75.0, null, th, 10));
        assertFalse(EnvironmentRules.nextState("mist", true, null, 96.0, null, th, 10));
        // Trong khoảng giữa: giữ nguyên trạng thái (tránh bật/tắt liên tục)
        assertTrue(EnvironmentRules.nextState("mist", true, null, 88.0, null, th, 10));
        assertFalse(EnvironmentRules.nextState("mist", false, null, 88.0, null, th, 10));
    }

    @Test
    void fanTurnsOnWhenHotOrCo2High() {
        assertTrue(EnvironmentRules.nextState("fan", false, 30.0, null, 700.0, th, 10));
        assertTrue(EnvironmentRules.nextState("fan", false, 25.0, null, 1200.0, th, 10));
        assertFalse(EnvironmentRules.nextState("fan", true, 25.0, null, 800.0, th, 10));
        // CO2 trong vùng 900-1000 ppm: giữ nguyên
        assertTrue(EnvironmentRules.nextState("fan", true, 25.0, null, 950.0, th, 10));
    }

    @Test
    void heaterTurnsOnWhenCold() {
        assertTrue(EnvironmentRules.nextState("heater", false, 18.0, null, null, th, 10));
        assertFalse(EnvironmentRules.nextState("heater", true, 21.5, null, null, th, 10));
        assertTrue(EnvironmentRules.nextState("heater", true, 20.5, null, null, th, 10));
    }

    @Test
    void lightFollowsSchedule() {
        assertTrue(EnvironmentRules.nextState("light", false, null, null, null, th, 6));
        assertTrue(EnvironmentRules.nextState("light", false, null, null, null, th, 17));
        assertFalse(EnvironmentRules.nextState("light", true, null, null, null, th, 18));
        assertFalse(EnvironmentRules.nextState("light", true, null, null, null, th, 2));
    }

    @Test
    void lightWindowAcrossMidnight() {
        assertTrue(EnvironmentRules.inWindow(23, 20, 6));
        assertTrue(EnvironmentRules.inWindow(3, 20, 6));
        assertFalse(EnvironmentRules.inWindow(12, 20, 6));
    }
}
