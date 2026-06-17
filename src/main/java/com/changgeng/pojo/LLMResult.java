package com.changgeng.prompt.pojo;
/* loaded from: LLMResult.class */
public class LLMResult {
    String response;

    public void setResponse(final String response) {
        this.response = response;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof LLMResult) {
            LLMResult other = (LLMResult) o;
            if (other.canEqual(this)) {
                Object this$response = getResponse();
                Object other$response = other.getResponse();
                return this$response == null ? other$response == null : this$response.equals(other$response);
            }
            return false;
        }
        return false;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof LLMResult;
    }

    public int hashCode() {
        Object $response = getResponse();
        int result = (1 * 59) + ($response == null ? 43 : $response.hashCode());
        return result;
    }

    public String toString() {
        return "LLMResult(response=" + getResponse() + ")";
    }

    public String getResponse() {
        return this.response;
    }
}