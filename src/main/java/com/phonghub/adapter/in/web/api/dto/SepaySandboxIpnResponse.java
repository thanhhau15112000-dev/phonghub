package com.phonghub.adapter.in.web.api.dto;

public record SepaySandboxIpnResponse(boolean success, String status, boolean duplicate, String error) {
    public static SepaySandboxIpnResponse ok(String status, boolean duplicate) {
        return new SepaySandboxIpnResponse(true, status, duplicate, null);
    }

    public static SepaySandboxIpnResponse error(String message) {
        return new SepaySandboxIpnResponse(false, null, false, message);
    }
}
