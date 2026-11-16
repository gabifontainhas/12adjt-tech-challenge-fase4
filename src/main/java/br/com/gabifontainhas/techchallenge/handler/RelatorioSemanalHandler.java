package br.com.gabifontainhas.techchallenge.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RelatorioSemanalHandler implements RequestHandler<Map<String, Object>, Void> {

    private final DynamoDbClient dynamoDbClient;
    private final SnsClient snsClient;
    private final String TOPIC_ARN = "arn:aws:sns:us-east-2:775524235851:AlertaFeedbackTopic";
    private final String TABLE_NAME = "Feedbacks";

    public RelatorioSemanalHandler() {
        this.dynamoDbClient = DynamoDbClient.create();
        this.snsClient = SnsClient.create();
    }

    public RelatorioSemanalHandler(DynamoDbClient dynamoDbClient, SnsClient snsClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.snsClient = snsClient;
    }

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        var logger = context.getLogger();
        logger.log("Iniciando geração do relatório semanal...");

        var feedbacks = buscarFeedbacks();

        if (feedbacks.isEmpty()) {
            logger.log("Nenhum feedback encontrado na tabela.");
            return null;
        }

        var relatorio = gerarCorpoDoRelatorio(feedbacks);
        enviarParaSns(relatorio);

        logger.log("Relatório processado e enviado com sucesso ao SNS.");
        return null;
    }

    private List<Map<String, AttributeValue>> buscarFeedbacks() {
        var scanRequest = ScanRequest.builder().tableName(TABLE_NAME).build();
        return dynamoDbClient.scan(scanRequest).items();
    }

    private String gerarCorpoDoRelatorio(List<Map<String, AttributeValue>> items) {
        var totalFeedbacks = items.size();
        var media = calcularMedia(items, totalFeedbacks);
        var porDia = agruparPorCampo(items, "dataEnvio", 10);
        var porUrgencia = agruparPorCampo(items, "urgencia", null);

        var relatorio = new StringBuilder();

        relatorio.append("Relatório Semanal de Feedbacks\n");
        relatorio.append("===================================\n");
        relatorio.append(String.format("Total de Avaliações: %d\n", totalFeedbacks));
        relatorio.append(String.format("Média Geral: %.2f\n\n", media));
        relatorio.append("--- Quantidade por Dia ---\n");

        porDia.forEach((dia, qtd) -> relatorio.append(dia).append(": ").append(qtd).append(" avaliação(ões)\n"));

        relatorio.append("\n--- Quantidade por Urgência ---\n");
        porUrgencia.forEach((urgencia, qtd) -> relatorio.append(urgencia).append(": ").append(qtd).append(" avaliação(ões)\n"));

        relatorio.append("\n--- Detalhamento dos Feedbacks ---\n");
        items.forEach(item -> {
            var desc = extrairValorSeguro(item, "descricao", "Sem descrição");
            var urg = extrairValorSeguro(item, "urgencia", "Não definida");
            var data = extrairValorSeguro(item, "dataEnvio", "Sem data");

            relatorio.append(String.format("- Data: %s | Urgência: %s | Descrição: %s\n", data, urg, desc));
        });

        return relatorio.toString();
    }

    private double calcularMedia(List<Map<String, AttributeValue>> items, int total) {
        var soma = items.stream()
                .mapToDouble(item -> Double.parseDouble(item.get("nota").n()))
                .sum();
        return soma / total;
    }

    private Map<String, Long> agruparPorCampo(List<Map<String, AttributeValue>> items, String campo, Integer limiteCaracteres) {
        return items.stream()
                .filter(item -> item.containsKey(campo) && item.get(campo).s() != null)
                .map(item -> {
                    var valor = item.get(campo).s();
                    return limiteCaracteres != null ? valor.substring(0, limiteCaracteres) : valor;
                })
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
    }

    private String extrairValorSeguro(Map<String, AttributeValue> item, String campo, String valorPadrao) {
        return item.containsKey(campo) ? item.get(campo).s() : valorPadrao;
    }

    private void enviarParaSns(String mensagem) {
        var snsRequest = PublishRequest.builder()
                .topicArn(TOPIC_ARN)
                .message(mensagem)
                .subject("Relatório Semanal - Tech Challenge")
                .build();
        snsClient.publish(snsRequest);
    }

}