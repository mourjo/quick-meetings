package me.mourjo.quickmeetings.web.dto;

import java.util.Map;

public record ValidationErrorResponse(String message, Map<String, String> reasons) {

}
