package com.recibos.recibos_service.util;

public class FonteDadoDTO {
    private String nomeArquivo;
    private String conteudo;

    public FonteDadoDTO(String nomeArquivo, String conteudo) {
        this.nomeArquivo = nomeArquivo;
        this.conteudo = conteudo;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getConteudo() {
        return conteudo;
    }
}