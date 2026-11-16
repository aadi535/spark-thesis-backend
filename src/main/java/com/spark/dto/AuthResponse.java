package com.spark.dto;

public class AuthResponse {
    private String token;
    private String email;
    private String name;
    private String image;
    private boolean isPro;

    public AuthResponse() {}

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public boolean isPro() { return isPro; }
    public void setPro(boolean isPro) { this.isPro = isPro; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AuthResponse r = new AuthResponse();
        public Builder token(String v) { r.token = v; return this; }
        public Builder email(String v) { r.email = v; return this; }
        public Builder name(String v) { r.name = v; return this; }
        public Builder image(String v) { r.image = v; return this; }
        public Builder isPro(boolean v) { r.isPro = v; return this; }
        public AuthResponse build() { return r; }
    }
}
