package com.recibos.recibos_service.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException; // Adicionado
// Imports de XML/SAX removidos

public class GeradorSQLRecibo {

    // private final ArquivoXML leitorXML; // Removido
    private final ArquivosESocial arquivosESocial;

    public GeradorSQLRecibo(boolean modoInsert) throws Exception { // Alterado
        // this.leitorXML = new ArquivoXML(); // Removido
        this.arquivosESocial = new ArquivosESocial(modoInsert); 
    }

    /**
     * --- MUDANÇA CRÍTICA ---
     * O método agora recebe o DTO pré-processado, e não o ficheiro.
     * Isto corrige o Erro 2 (Linha 97).
     */
    public String gerarSQL(InfoReciboDTO info, String tipoEvento)
            throws IOException {

        String sql = "";

        // Usa o DTO 'info' recebido por parâmetro
        switch (tipoEvento) {
            case "2200.xml":
                sql = arquivosESocial.s2200(info.getMatricula(), info.getRecibo());
                break;
            // --- CASE ADICIONADO ---
            case "2300.xml":
                sql = arquivosESocial.s2300(info.getMatricula(), info.getRecibo());
                break;
            // --- FIM DA ADIÇÃO ---
            case "2299.xml":
                sql = arquivosESocial.s2299(info.getMatricula(), info.getRecibo());
                break;
            case "1200.xml":
                sql = arquivosESocial.s1200(info.getCpf(), info.getRecibo(), info.getPerApur());
                break;
            case "1202.xml":
                sql = arquivosESocial.s1202(info.getCpf(), info.getRecibo(), info.getPerApur());
                break;
            case "1210.xml":
                sql = arquivosESocial.s1210(info.getCpf(), info.getRecibo(), info.getPerApur());
                break;
            case "3000.xml":
                sql = arquivosESocial.s3000(info.getNrRecEvt()); 
                break;
            default:
                throw new IllegalArgumentException("Tipo de arquivo XML não reconhecido: " + tipoEvento);
        }

        return sql;
    }
}