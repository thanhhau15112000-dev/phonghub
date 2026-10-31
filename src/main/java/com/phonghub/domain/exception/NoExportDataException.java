package com.phonghub.domain.exception;

/** Bộ lọc không có dòng dữ liệu nào để xuất (#227). */
public class NoExportDataException extends DomainException {
    public NoExportDataException() {
        super("Không có dữ liệu phù hợp để xuất.");
    }
}
