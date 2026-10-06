package com.hcm.postmangen.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * One entry from the API inventory input file.
 * path      -> ordered folder breadcrumb, e.g. ["Core HCM", "Employee Profile
 *              Management", "View Personal Details"]. Folders are created/
 *              reused at each level, so requests that share a path segment
 *              land in the same folder.
 * name      -> becomes the request's display name
 * curl      -> the actual curl command for this API
 * sampleResponse -> arbitrary JSON attached as a saved example response
 */
public class ApiEntry {

    private List<String> path;
    private String name;
    private String curl;
    private JsonNode sampleResponse;
    private List<String> testScript;
    private List<String> preRequestScript;

    public List<String> getPath() {
        return path;
    }

    public void setPath(List<String> path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCurl() {
        return curl;
    }

    public void setCurl(String curl) {
        this.curl = curl;
    }

    public JsonNode getSampleResponse() {
        return sampleResponse;
    }

    public void setSampleResponse(JsonNode sampleResponse) {
        this.sampleResponse = sampleResponse;
    }

    public List<String> getTestScript() {
        return testScript;
    }

    public void setTestScript(List<String> testScript) {
        this.testScript = testScript;
    }

    public List<String> getPreRequestScript() {
        return preRequestScript;
    }

    public void setPreRequestScript(List<String> preRequestScript) {
        this.preRequestScript = preRequestScript;
    }
}