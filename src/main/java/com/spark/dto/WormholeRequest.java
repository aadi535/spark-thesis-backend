package com.spark.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record WormholeRequest(String question, @JsonAlias("title") String sparkTitle,
        @JsonAlias("description") String sparkDescription, List<ChatTurn> history) {
    public record ChatTurn(String role, String content) {}
}
