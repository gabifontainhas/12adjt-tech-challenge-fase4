package br.com.gabifontainhas.techchallenge.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class FeedbackStreamHandler implements RequestHandler<DynamodbEvent, Void> {

    private final SnsClient snsClient = SnsClient.create();
    private final String TOPIC_ARN = "arn:aws:sns:us-east-2:775524235851:AlertaFeedbackTopic";

    @Override
    public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : dynamodbEvent.getRecords()) {
            if ("INSERT".equals(record.getEventName())) {
                StreamRecord streamRecord = record.getDynamodb();

                String id = streamRecord.getNewImage().get("id").getS();
                String descricao = streamRecord.getNewImage().get("descricao").getS();

                String nota = streamRecord.getNewImage().get("nota").getN();
                int valorNota = Integer.parseInt(nota);
                if (valorNota <= 4) {
                    String mensagem = String.format("URGENTE! Feedback Crítico recebido.\n\nID: %s\nNota: %s\nDescrição: %s", id, nota, descricao);
                    PublishRequest request = PublishRequest.builder()
                            .topicArn(TOPIC_ARN)
                            .message(mensagem)
                            .subject("ALERTA CRÍTICO: Novo Feedback Negativo")
                            .build();
                    snsClient.publish(request);
                    context.getLogger().log("Alerta crítico enviado para o ID: " + id);
                } else {
                    context.getLogger().log("Feedback positivo (Nota " + valorNota + "). Nenhuma notificação de urgência necessária.");
                }
            }
        }
        return null;
    }
}