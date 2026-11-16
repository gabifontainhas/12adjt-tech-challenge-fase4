package br.com.gabifontainhas.techchallenge.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;
public class FeedbackStreamHandler implements RequestHandler<DynamodbEvent, Void> {

    private final SnsClient snsClient;
    private final String TOPIC_ARN = "arn:aws:sns:us-east-2:775524235851:AlertaFeedbackTopic";

    public FeedbackStreamHandler() {
        this.snsClient = SnsClient.create();
    }

    public FeedbackStreamHandler(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    @Override
    public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
        var logger = context.getLogger();

        for (var record : dynamodbEvent.getRecords()) {
            if ("INSERT".equals(record.getEventName())) {
                processarNovoFeedback(record.getDynamodb().getNewImage(), logger);
            }
        }
        return null;
    }

    private void processarNovoFeedback(Map<String, AttributeValue> novaImagem, com.amazonaws.services.lambda.runtime.LambdaLogger logger) {
        var notaStr = extrairAtributoSeguro(novaImagem, "nota", "N");

        if ("Não informado".equals(notaStr)) {
            logger.log("Registro sem nota. Ignorando.");
            return;
        }

        var valorNota = Integer.parseInt(notaStr);

        if (valorNota <= 4) {
            var descricao = extrairAtributoSeguro(novaImagem, "descricao", "S");
            var urgencia = extrairAtributoSeguro(novaImagem, "urgencia", "S");
            var dataEnvio = extrairAtributoSeguro(novaImagem, "dataEnvio", "S");

            var mensagem = formatarMensagemAlerta(descricao, urgencia, dataEnvio, valorNota);
            enviarAlertaSns(mensagem);

            logger.log("Alerta crítico enviado. Data: " + dataEnvio);
        } else {
            logger.log("Feedback não crítico (Nota " + valorNota + "). Nenhuma notificação enviada.");
        }
    }

    private String extrairAtributoSeguro(Map<String, AttributeValue> imagem, String chave, String tipo) {
        if (!imagem.containsKey(chave) || imagem.get(chave) == null) {
            return "Não informado";
        }
        var atributo = imagem.get(chave);
        return "N".equals(tipo) ? atributo.getN() : atributo.getS();
    }

    private String formatarMensagemAlerta(String descricao, String urgencia, String dataEnvio, int nota) {
        var mensagem = new StringBuilder();

        mensagem.append("URGENTE! Feedback Crítico recebido.\n\n");
        mensagem.append("--- Detalhes da Avaliação ---\n");
        mensagem.append(String.format("Nota: %d\n", nota));
        mensagem.append(String.format("Descrição: %s\n", descricao));
        mensagem.append(String.format("Urgência: %s\n", urgencia));
        mensagem.append(String.format("Data de envio: %s\n", dataEnvio));

        return mensagem.toString();
    }

    private void enviarAlertaSns(String mensagem) {
        var request = PublishRequest.builder()
                .topicArn(TOPIC_ARN)
                .message(mensagem)
                .subject("ALERTA CRÍTICO: Novo Feedback Negativo")
                .build();
        snsClient.publish(request);
    }
}