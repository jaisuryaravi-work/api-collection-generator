package com.hcm.postmangen.curl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured result of parsing a curl command string.
 *
 * authType, when set to "bearer" or "basic", means the Authorization was
 * recognized and pulled out of the headers so it can be written into
 * Postman's structured "auth" field (Auth tab) instead of a plain header.
 *
 * formData holds --form / -F fields (multipart/form-data). When non-empty,
 * the request body should be built as Postman's "formdata" mode instead
 * of "raw", and body/method-from-body still apply the same POST default.
 */
public class ParsedCurl {

    private String method = "GET";
    private String url;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String body;
    private final List<FormField> formData = new ArrayList<>();
    private final List<FormField> urlEncodedData = new ArrayList<>();
    
    public List<FormField> getUrlEncodedData() {
        return urlEncodedData;
    }

    private String authType; // "bearer", "basic", or null
    private String bearerToken;
    private String basicUsername;
    private String basicPassword;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<FormField> getFormData() {
        return formData;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getBasicUsername() {
        return basicUsername;
    }

    public void setBasicUsername(String basicUsername) {
        this.basicUsername = basicUsername;
    }

    public String getBasicPassword() {
        return basicPassword;
    }

    public void setBasicPassword(String basicPassword) {
        this.basicPassword = basicPassword;
    }
}