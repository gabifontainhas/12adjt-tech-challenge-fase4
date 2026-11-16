package br.com.gabifontainhas.techchallenge.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackStreamHandlerTest {

    @Mock
    private SnsClient snsClient;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    @InjectMocks
    private FeedbackStreamHandler handler;

    @BeforeEach
    void setUp() {
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    void deveEnviarAlertaSnsQuandoNotaForMenorOuIgualAQuatro() {
        // Arrange: Prepara um evento do DynamoDB com nota 3 (Crítica)
        var streamRecord = new StreamRecord();
        streamRecord.setNewImage(Map.of(
                "nota", new AttributeValue().withN("3"),
                "descricao", new AttributeValue().withS("Péssimo"),
                "urgencia", new AttributeValue().withS("ALTA"),
                "dataEnvio", new AttributeValue().withS("2026-11-13T12:00:00Z")
        ));

        var record = new DynamodbEvent.DynamodbStreamRecord();
        record.setEventName("INSERT");
        record.setDynamodb(streamRecord);

        var event = new DynamodbEvent();
        event.setRecords(List.of(record));

        // Act
        handler.handleRequest(event, context);

        // Assert: Garante que o SNS foi chamado para enviar o alerta
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
        verify(logger).log("Alerta crítico enviado. Data: 2026-11-13T12:00:00Z");
    }

    @Test
    void naoDeveEnviarAlertaQuandoNotaForMaiorQueQuatro() {
        // Arrange: Prepara um evento com nota 8 (Positiva)
        var streamRecord = new StreamRecord();
        streamRecord.setNewImage(Map.of(
                "nota", new AttributeValue().withN("8"),
                "descricao", new AttributeValue().withS("Muito bom"),
                "urgencia", new AttributeValue().withS("BAIXA"),
                "dataEnvio", new AttributeValue().withS("2026-11-13T12:00:00Z")
        ));

        var record = new DynamodbEvent.DynamodbStreamRecord();
        record.setEventName("INSERT");
        record.setDynamodb(streamRecord);

        var event = new DynamodbEvent();
        event.setRecords(List.of(record));

        // Act
        handler.handleRequest(event, context);

        // Assert: Garante que o SNS NUNCA foi chamado
        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(logger).log("Feedback não crítico (Nota 8). Nenhuma notificação enviada.");
    }
}