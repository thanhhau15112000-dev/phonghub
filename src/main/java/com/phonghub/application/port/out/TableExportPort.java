package com.phonghub.application.port.out;

import java.util.List;

/** Dựng một bảng dữ liệu thành tệp tải về (Excel hoặc PDF) — #85. */
public interface TableExportPort {

    byte[] render(Table table);

    String contentType();

    String extension();

    /**
     * Ô có thể là {@code String}, {@code Number} (giữ kiểu số trong Excel) hoặc {@code java.time.LocalDate};
     * null là ô trống.
     */
    record Table(String title, String subtitle, List<String> headers, List<List<Object>> rows) {
        public Table {
            headers = List.copyOf(headers);
            rows = List.copyOf(rows);
        }
    }
}
