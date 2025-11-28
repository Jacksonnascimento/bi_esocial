package com.recibos.recibos_service.util;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatusSessaoService {

    // Um mapa seguro para concorrência para armazenar o estado das sessões
    // Chave: sessaoId, Valor: Objeto de Status
    private final Map<String, StatusSessao> statusSessoes = new ConcurrentHashMap<>();

    public void iniciarSessao(String sessaoId) {
        statusSessoes.put(sessaoId, new StatusSessao("PROCESSANDO", "O processamento foi iniciado..."));
    }

    public void completarSessao(String sessaoId, int totalArquivos) {
        String mensagem = "Processamento concluído com sucesso. " + totalArquivos + " scripts SQL foram gerados.";
        statusSessoes.put(sessaoId, new StatusSessao("CONCLUIDO", mensagem));
    }

    public void falharSessao(String sessaoId, String erro) {
        statusSessoes.put(sessaoId, new StatusSessao("FALHA", "Erro: " + erro));
    }

    public StatusSessao getStatus(String sessaoId) {
        return statusSessoes.getOrDefault(sessaoId, new StatusSessao("NAO_ENCONTRADO", "Sessão não encontrada."));
    }

    // Classe interna para armazenar o status e uma mensagem
    public static class StatusSessao {
        private String status;
        private String mensagem;

        public StatusSessao(String status, String mensagem) {
            this.status = status;
            this.mensagem = mensagem;
        }

        public String getStatus() {
            return status;
        }

        public String getMensagem() {
            return mensagem;
        }
    }
}