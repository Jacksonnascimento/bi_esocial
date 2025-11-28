package com.recibos.recibos_service.util;

// Usando Lombok, que já está no seu pom.xml
import lombok.Data;

@Data
public class InfoReciboDTO {
    private String id;
    private String recibo;
    private String cpf;
    private String matricula;
    private String perApur;
    private String nrRecEvt;
    
    // Campo adicionado
    private String dhProcessamento;

    /**
     * --- MUDANÇA: MÉTODO CORRIGIDO (LÓGICA FINAL) ---
     * Cria uma chave de deduplicação para garantir que pegamos o recibo 
     * mais recente PARA CADA TIPO DE EVENTO por CPF (ou Matrícula).
     *
     * @param tipoEvento O nome do arquivo (ex: "1200.xml")
     * @return Uma String de chave única (ex: "CPF:12345678900:1200.xml")
     */
    public String getDeduplicationKey(String tipoEvento) {
        
        // --- LÓGICA CORRIGIDA ---
        // Prioridade 1: Eventos com CPF (S-1200, S-1202, S-1210)
        // A chave será composta pelo CPF + TIPO DO EVENTO.
        if (this.cpf != null && !this.cpf.isEmpty()) {
            return "CPF:" + this.cpf + ":" + tipoEvento;
        }

        // Prioridade 2: Eventos com Matrícula (S-2200, S-2299)
        // A chave será composta pela MATRÍCULA + TIPO DO EVENTO.
        if (this.matricula != null && !this.matricula.isEmpty()) {
            return "MAT:" + this.matricula + ":" + tipoEvento;
        }
        
         // Prioridade 3: Evento de Exclusão (S-3000)
         // A chave é o recibo que ele está excluindo.
        if ("3000.xml".equals(tipoEvento)) {
            return "S3000:" + (this.nrRecEvt != null ? this.nrRecEvt : "null");
        }
        
        // Chave de fallback (baseada no ID do evento)
        return tipoEvento + ":" + (this.id != null ? this.id : "fallback");
    }

    /**
     * Compara este DTO com outro, com base no dhProcessamento.
     * @param outro O DTO existente no mapa.
     * @return true se este DTO for mais recente que o 'outro'.
     */
    public boolean isMaisRecenteQue(InfoReciboDTO outro) {
        // Se o outro não existe ou não tem data, "este" é considerado mais recente.
        if (outro == null || outro.getDhProcessamento() == null) {
            return true; 
        }
        // Se "este" não tem data, ele não pode ser mais recente.
        if (this.getDhProcessamento() == null) {
            return false; 
        }
        
        // Compara as strings de data/hora (formato ISO 8601 permite comparação textual)
        return this.getDhProcessamento().compareTo(outro.getDhProcessamento()) > 0;
    }
}