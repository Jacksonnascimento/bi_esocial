package com.recibos.recibos_service.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ProcessadorEsocialLib {

    /**
     * Método principal da biblioteca.
     */
    public String gerarScript(String caminhoPasta, String tipoEventoFiltro, String filtroCompetencia, int orgCod, int ambiente, int usrCod) {
        
        // Normalização do Filtro
        if (tipoEventoFiltro == null || tipoEventoFiltro.trim().isEmpty()) {
            tipoEventoFiltro = "T";
        }
        
        final String filtroFinal = tipoEventoFiltro;

        Map<String, InfoReciboDTO> dtosUnicos = new HashMap<>();
        ArquivoXML leitorXML = new ArquivoXML();
        GeradorSQLRecibo geradorSQL = new GeradorSQLRecibo();

        // 1. LEITURA
        try (Stream<Path> paths = Files.walk(Paths.get(caminhoPasta))) {
            
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                 .forEach(path -> {
                     File arquivo = path.toFile();
                     String nomeArquivo = arquivo.getName();

                     // Filtro de compatibilidade
                     if (!filtroFinal.equals("T")) {
                         if (!isArquivoCompativelComFiltro(nomeArquivo, filtroFinal)) {
                             return; 
                         }
                     }

                     try {
                         InfoReciboDTO info = leitorXML.infXML(arquivo, nomeArquivo);
                         
                         if (info.getId() == null && info.getRecibo() == null) {
                             return; 
                         }
                         
                         if (filtroCompetencia != null && !filtroCompetencia.isEmpty()) {
                             if (info.getPerApur() != null && !info.getPerApur().isEmpty()) {
                                 if (!info.getPerApur().equals(filtroCompetencia)) {
                                     return; 
                                 }
                             }
                         }

                         String tipoEventoDetectado = detectTipo(nomeArquivo); 
                         String chave = info.getDeduplicationKey(tipoEventoDetectado);
                         
                         InfoReciboDTO existente = dtosUnicos.get(chave);
                         if (existente == null || info.isMaisRecenteQue(existente)) {
                             dtosUnicos.put(chave, info);
                         }

                     } catch (Exception e) {
                         System.err.println("Erro ao ler " + nomeArquivo + ": " + e.getMessage());
                     }
                 });

        } catch (IOException e) {
            // Em caso de erro de IO, ainda retornamos o erro para saber o que houve,
            // ou você pode retornar "" aqui também se preferir silêncio total.
            // Por segurança, mantive o erro, mas se quiser vazio mude para return "";
            return "-- ERRO AO ACESSAR PASTA: " + e.getMessage();
        }

        // 2. ESCRITA
        // Se não achou nada, retorna VAZIO
        if (dtosUnicos.isEmpty()) {
            return ""; 
        }

        StringBuilder corpoScripts = new StringBuilder();

        for (InfoReciboDTO info : dtosUnicos.values()) {
             String tipo = detectTipo(info.getNomeArquivo());
             
             if (!filtroFinal.equals("T")) {
                 if (!isArquivoCompativelComFiltro(info.getNomeArquivo(), filtroFinal)) continue;
             }

             String sql = geradorSQL.gerarSQL(info, tipo, orgCod, ambiente, usrCod);
             
             if (sql != null) {
                 corpoScripts.append(sql);
             }
        }

        // Se após processar tudo, não gerou nenhum script (ex: arquivos sem recibo), retorna VAZIO
        if (corpoScripts.length() == 0) {
            return "";
        }

        StringBuilder scriptFinal = new StringBuilder();
        scriptFinal.append("-- SCRIPT GERADO PELA BIBLIOTECA DE IMPORTAÇÃO DE RECIBOS\n");
        scriptFinal.append("-- Data/Hora Geração: ").append(java.time.LocalDateTime.now()).append("\n\n");
        scriptFinal.append(corpoScripts);

        return scriptFinal.toString();
    }
    
    private boolean isArquivoCompativelComFiltro(String nomeArquivo, String filtro) {
        String f = filtro.toUpperCase();
        String n = nomeArquivo.toUpperCase(); 

        if (f.equals("T")) return true;

        if (f.contains("2200")) return n.contains("2200") || n.contains("EVTADMISSAO");
        if (f.contains("2299")) return n.contains("2299") || n.contains("EVTDESLIG");
        if (f.contains("2300")) return n.contains("2300") || n.contains("EVTTSVINICIO"); 
        if (f.contains("1200")) return n.contains("1200") || n.contains("EVTREMUN");
        if (f.contains("1210")) return n.contains("1210") || n.contains("EVTPGTOS");
        if (f.contains("1202")) return n.contains("1202") || n.contains("EVTRMNRPPS");
        
        // Fallback genérico: se o filtro for parte do nome do arquivo
        return n.contains(f);
    }
    
    private String detectTipo(String nomeArquivo) {
        String n = nomeArquivo.toUpperCase();
        if (n.contains("2200") || n.contains("EVTADMISSAO")) return "S-2200";
        if (n.contains("2299") || n.contains("EVTDESLIG")) return "S-2299";
        if (n.contains("2300") || n.contains("EVTTSVINICIO")) return "S-2300";
        if (n.contains("1200") || n.contains("EVTREMUN")) return "S-1200";
        if (n.contains("1210") || n.contains("EVTPGTOS")) return "S-1210";
        if (n.contains("1202") || n.contains("EVTRMNRPPS")) return "S-1202";
        return nomeArquivo; 
    }
}