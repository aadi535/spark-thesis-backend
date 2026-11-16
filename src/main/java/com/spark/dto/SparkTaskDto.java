package com.spark.dto;

import java.util.List;

public class SparkTaskDto {
    private String category;
    private String icon;
    private String color;
    private String title;
    private String hook;
    private String description;
    private List<String> steps;
    private String funFact;
    private int xp;
    private String badge;
    private boolean isAI;
    private SparkLink link;

    public SparkTaskDto() {}

    // Getters & Setters
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getHook() { return hook; }
    public void setHook(String hook) { this.hook = hook; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }
    public String getFunFact() { return funFact; }
    public void setFunFact(String funFact) { this.funFact = funFact; }
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }
    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }
    public boolean isAI() { return isAI; }
    public void setAI(boolean isAI) { this.isAI = isAI; }
    public SparkLink getLink() { return link; }
    public void setLink(SparkLink link) { this.link = link; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final SparkTaskDto t = new SparkTaskDto();
        public Builder category(String v) { t.category = v; return this; }
        public Builder icon(String v) { t.icon = v; return this; }
        public Builder color(String v) { t.color = v; return this; }
        public Builder title(String v) { t.title = v; return this; }
        public Builder hook(String v) { t.hook = v; return this; }
        public Builder description(String v) { t.description = v; return this; }
        public Builder steps(List<String> v) { t.steps = v; return this; }
        public Builder funFact(String v) { t.funFact = v; return this; }
        public Builder xp(int v) { t.xp = v; return this; }
        public Builder badge(String v) { t.badge = v; return this; }
        public Builder isAI(boolean v) { t.isAI = v; return this; }
        public Builder link(SparkLink v) { t.link = v; return this; }
        public SparkTaskDto build() { return t; }
    }

    public static class SparkLink {
        private String text;
        private String url;
        public SparkLink() {}
        public SparkLink(String text, String url) { this.text = text; this.url = url; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
