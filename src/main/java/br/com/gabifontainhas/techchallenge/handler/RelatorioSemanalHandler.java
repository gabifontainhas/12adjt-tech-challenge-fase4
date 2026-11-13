package br.com.gabifontainhas.techchallenge.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

public class RelatorioSemanalHandler implements RequestHandler<Map<String, Object>, Void> {
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SnsClient snsClient = SnsClient.create();
    private final String TOPIC_ARN = "arn:aws:sns:us-east-2:775524235851:AlertaFeedbackTopic";
    private final String TABLE_NAME = "Feedbacks";

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        context.getLogger().log("Iniciando geração do relatório semanal...");

        ScanRequest scanRequest = ScanRequest.builder().tableName(TABLE_NAME).build();
        ScanResponse result = dynamoDbClient.scan(scanRequest);

        int totalFeedbacks = result.count();
        if (totalFeedbacks == 0) {
            context.getLogger().log("Nenhum feedback encontrado.");
            return null;
        }

        double somaNotas = 0;
        for (Map<String, AttributeValue> item : result.items()) {
            somaNotas += Double.parseDouble(item.get("nota").n());
        }
        double media = somaNotas / totalFeedbacks;

        String relatorio = String.format("Relatório Semanal de Feedbacks\n\nTotal de Avaliações: %d\nMédia Geral: %.2f", totalFeedbacks, media);
        PublishRequest snsRequest = PublishRequest.builder()
                .topicArn(TOPIC_ARN)
                .message(relatorio)
                .subject("Relatório Semanal - Tech Challenge")
                .build();

        snsClient.publish(snsRequest);
        context.getLogger().log("Relatório enviado! Média: " + media);

        return null;
    }
}
