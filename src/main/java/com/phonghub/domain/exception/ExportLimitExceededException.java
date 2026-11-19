package com.phonghub.domain.exception;

/** Số dòng xuất vượt giới hạn mỗi lần (#227). */
public class ExportLimitExceededException extends DomainException {
    public ExportLimitExceededException(int limit) {
        super("Một lần xuất tối đa " + limit + " dòng. Hãy lọc hẹp lại hoặc chọn bớt dòng.");
    }
}
