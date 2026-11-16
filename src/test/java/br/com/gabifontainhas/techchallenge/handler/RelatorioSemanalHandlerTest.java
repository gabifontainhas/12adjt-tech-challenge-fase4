package br.com.gabifontainhas.techchallenge.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelatorioSemanalHandlerTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SnsClient snsClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    @InjectMocks
    private RelatorioSemanalHandler handler;

    @BeforeEach
    void setUp() {
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void deveGerarRelatorioEEnviarParaSnsComSucesso() {
        // Arrange: Prepara os dados falsos simulando o retorno do DynamoDB
        var item1 = Map.of(
                "nota", AttributeValue.builder().n("8").build(),
                "dataEnvio", AttributeValue.builder().s("2026-11-13T10:00:00Z").build(),
                "urgencia", AttributeValue.builder().s("BAIXA").build(),
                "descricao", AttributeValue.builder().s("Aula muito boa").build()
        );

        var item2 = Map.of(
                "nota", AttributeValue.builder().n("2").build(),
                "dataEnvio", AttributeValue.builder().s("2026-11-13T14:30:00Z").build(),
                "urgencia", AttributeValue.builder().s("ALTA").build(),
                "descricao", AttributeValue.builder().s("Professor atrasou").build()
        );

        var scanResponse = ScanResponse.builder()
                .items(List.of(item1, item2))
                .build();

        // Ensina o mock do Dynamo a retornar a nossa lista falsa
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Act: Executa o método principal da Lambda
        handler.handleRequest(Map.of(), context);

        // Assert: Verifica se o cliente SNS foi chamado exatamente 1 vez com os dados formatados
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
        verify(logger).log("Relatório processado e enviado com sucesso ao SNS.");
    }

    @Test
    void deveIgnorarEnvioQuandoNaoHouverFeedbacks() {
        // Arrange: Simula um banco de dados vazio
        var scanResponse = ScanResponse.builder()
                .items(List.of())
                .build();

        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Act
        handler.handleRequest(Map.of(), context);

        // Assert: Garante que o SNS NUNCA foi chamado e o log correto foi impresso
        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(logger).log("Nenhum feedback encontrado na tabela.");
    }
}
