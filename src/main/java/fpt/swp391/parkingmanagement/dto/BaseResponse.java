package fpt.swp391.parkingmanagement.dto;

import lombok.Data;

@Data
public class BaseResponse {
    private int code;
    private String message;
    private Object data;
}
