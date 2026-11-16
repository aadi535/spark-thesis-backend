package com.spark.dto;

import java.util.List;

public class SparkRequest {
    private String purpose = "manual";
    private String question;
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    private String mood;
    private String time;
    private String interests;
    private List<String> seenTasks;

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }
    public List<String> getSeenTasks() { return seenTasks; }
    public void setSeenTasks(List<String> seenTasks) { this.seenTasks = seenTasks; }
}
