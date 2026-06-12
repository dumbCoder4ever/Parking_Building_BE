package fpt.swp391.parkingmanagement.exception;

import lombok.Getter;

@Getter
public class BaseAPIException extends RuntimeException {

    private final ErrorCode errorCode;

    public BaseAPIException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseAPIException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
