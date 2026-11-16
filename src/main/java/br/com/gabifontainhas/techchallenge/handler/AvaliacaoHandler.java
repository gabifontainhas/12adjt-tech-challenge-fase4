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

    private final Gson gson;
    private final DynamoDbClient dynamoDb;
    private final String TABLE_NAME = "Feedbacks";

    public AvaliacaoHandler() {
        this.gson = new Gson();
        this.dynamoDb = DynamoDbClient.create();
    }

    public AvaliacaoHandler(Gson gson, DynamoDbClient dynamoDb) {
        this.gson = gson;
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        var logger = context.getLogger();
        logger.log("Recebendo requisição no POST /avaliacao...");

        try {
            var body = input.getBody();
            var feedback = gson.fromJson(body, FeedbackRequest.class);

            if (!isValido(feedback)) {
                return criarResposta(400, "Dados inválidos. A nota deve ser entre 0 e 10 e a descrição é obrigatória.");
            }

            var id = UUID.randomUUID().toString();
            var item = montarItemDynamo(id, feedback);

            var putRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();

            dynamoDb.putItem(putRequest);
            logger.log("Avaliação salva com sucesso. ID: " + id);

            return criarResposta(201, "Avaliação registrada com sucesso! ID: " + id);

        } catch (Exception e) {
            logger.log("Erro interno: " + e.getMessage());
            return criarResposta(500, "Erro ao processar a avaliação.");
        }
    }

    private boolean isValido(FeedbackRequest feedback) {
        return feedback != null
                && feedback.descricao() != null
                && !feedback.descricao().trim().isEmpty()
                && feedback.nota() >= 0
                && feedback.nota() <= 10;
    }

    private Map<String, AttributeValue> montarItemDynamo(String id, FeedbackRequest feedback) {
        var dataEnvio = Instant.now().toString();

        var urgencia = feedback.nota() <= 4 ? "ALTA" : "BAIXA";

        return Map.of(
                "id", AttributeValue.builder().s(id).build(),
                "descricao", AttributeValue.builder().s(feedback.descricao()).build(),
                "nota", AttributeValue.builder().n(String.valueOf(feedback.nota())).build(),
                "dataEnvio", AttributeValue.builder().s(dataEnvio).build(), // Atualizado para camelCase
                "urgencia", AttributeValue.builder().s(urgencia).build() // Adicionando o campo que as outras Lambdas precisam
        );
    }

    private APIGatewayProxyResponseEvent criarResposta(int statusCode, String mensagem) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(gson.toJson(Map.of("mensagem", mensagem)));
    }
}