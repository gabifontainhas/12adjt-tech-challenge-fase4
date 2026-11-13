package br.com.gabifontainhas.techchallenge.model;

public record FeedbackRequest(
        String descricao,
        int nota
) {}