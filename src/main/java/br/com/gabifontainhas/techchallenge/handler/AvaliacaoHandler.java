package br.com.gabifontainhas.techchallenge.handler;

import br.com.gabifontainhas.techchallenge.model.FeedbackRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AvaliacaoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final Gson gson = new Gson();
    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final String TABLE_NAME = "Feedbacks";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Recebendo requisição no POST /avaliação...");

        try {
            String body = input.getBody();
            FeedbackRequest feedback = gson.fromJson(body, FeedbackRequest.class);

            if (feedback.descricao() == null || feedback.nota() < 0 || feedback.nota() > 10) {
                return criarResposta(400, "Dados inválidos. A nota deve ser entre 0 e 10.");
            }

            String id = UUID.randomUUID().toString();
            String dataEnvio = Instant.now().toString();

            Map<String, AttributeValue> item = Map.of(
                    "id", AttributeValue.builder().s(id).build(),
                    "descricao", AttributeValue.builder().s(feedback.descricao()).build(),
                    "nota", AttributeValue.builder().n(String.valueOf(feedback.nota())).build(),
                    "data_envio", AttributeValue.builder().s(dataEnvio).build()
            );

            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();

            dynamoDb.putItem(putRequest);
            context.getLogger().log("Avaliação salva com sucesso. ID: " + id);

            return criarResposta(201, "Avaliação registrada com sucesso! ID: " + id);

        } catch (Exception e) {
            context.getLogger().log("Erro interno: " + e.getMessage());
            return criarResposta(500, "Erro ao processar a avaliação.");
        }
    }

    private APIGatewayProxyResponseEvent criarResposta(int statusCode, String mensagem) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(gson.toJson(Map.of("mensagem", mensagem)));
    }
}