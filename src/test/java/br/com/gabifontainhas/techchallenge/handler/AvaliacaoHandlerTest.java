package br.com.gabifontainhas.techchallenge.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvaliacaoHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private Gson gson;

    private AvaliacaoHandler handler;

    @BeforeEach
    void setUp() {
        gson = new Gson();
        handler = new AvaliacaoHandler(gson, dynamoDbClient);
        lenient().when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void deveSalvarAvaliacaoComSucessoERetornar201() {
        // Arrange
        var requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setBody("{\"nota\": 8, \"descricao\": \"Ótima plataforma!\"}");

        // Act
        var response = handler.handleRequest(requestEvent, context);

        // Assert
        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("Avaliação registrada com sucesso"));

        // Verifica se o DynamoDB foi chamado para inserir o item
        verify(dynamoDbClient, times(1)).putItem(any(PutItemRequest.class));
    }

    @Test
    void deveRetornar400ParaNotaInvalida() {
        // Arrange (Nota 15 é inválida)
        var requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setBody("{\"nota\": 15, \"descricao\": \"Excelente!\"}");

        // Act
        var response = handler.handleRequest(requestEvent, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Dados inválidos"));

        // Dynamo não deve ser chamado
        verify(dynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void deveRetornar400ParaDescricaoNula() {
        // Arrange
        var requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setBody("{\"nota\": 5}"); // Sem descrição

        // Act
        var response = handler.handleRequest(requestEvent, context);

        // Assert
        assertEquals(400, response.getStatusCode());

        verify(dynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void deveRetornar500SeBancoFalhar() {
        // Arrange
        var requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setBody("{\"nota\": 10, \"descricao\": \"Tudo perfeito!\"}");

        // Simula falha de rede/banco
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenThrow(new RuntimeException("Dynamo offline"));

        // Act
        var response = handler.handleRequest(requestEvent, context);

        // Assert
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Erro ao processar a avaliação"));
    }
}