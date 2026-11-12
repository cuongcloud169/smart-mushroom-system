package com.mushroom.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Kiểm thử xuất CSV: có BOM UTF-8, escape dấu ngoặc kép, ô rỗng khi null */
class ReportCsvTest {
    @Test
    void csvEscapesQuotesAndNulls() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Phòng \"A1\"");
        row.put("note", null);
        row.put("n", 3);
        String csv = ReportController.toCsv(List.of(row));
        assertTrue(csv.startsWith("﻿"));
        assertEquals("﻿name,note,n\n\"Phòng \"\"A1\"\"\",,\"3\"\n", csv);
    }

    @Test
    void emptyRowsGiveOnlyBom() {
        assertEquals("﻿", ReportController.toCsv(List.of()));
    }
}
