package com.recibos.recibos_service.util;

public class InfoReciboDTO {
    // Campos
    private String id;
    private String recibo;
    private String cpf;
    private String matricula;
    private String perApur;
    private String dhProcessamento;
    private String protocolo;
    private String nomeArquivo;
    private String caminhoArquivo;

    // --- GETTERS E SETTERS ---
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRecibo() { return recibo; }
    public void setRecibo(String recibo) { this.recibo = recibo; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public String getPerApur() { return perApur; }
    public void setPerApur(String perApur) { this.perApur = perApur; }

    public String getDhProcessamento() { return dhProcessamento; }
    public void setDhProcessamento(String dhProcessamento) { this.dhProcessamento = dhProcessamento; }

    public String getProtocolo() { return protocolo; }
    public void setProtocolo(String protocolo) { this.protocolo = protocolo; }

    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }

    public String getCaminhoArquivo() { return caminhoArquivo; }
    public void setCaminhoArquivo(String caminhoArquivo) { this.caminhoArquivo = caminhoArquivo; }

    // --- LÓGICA DE DEDUPLICAÇÃO E COMPARAÇÃO ---

    public String getDeduplicationKey(String tipoEvento) {
        if (this.cpf != null && !this.cpf.isEmpty()) {
            return "CPF:" + this.cpf + ":" + tipoEvento;
        }
        if (this.matricula != null && !this.matricula.isEmpty()) {
            return "MAT:" + this.matricula + ":" + tipoEvento;
        }
        return tipoEvento + ":" + (this.id != null ? this.id : "fallback");
    }

    public boolean isMaisRecenteQue(InfoReciboDTO outro) {
        if (outro == null || outro.getDhProcessamento() == null) {
            return true; 
        }
        if (this.getDhProcessamento() == null) {
            return false; 
        }
        return this.getDhProcessamento().compareTo(outro.getDhProcessamento()) > 0;
    }
}