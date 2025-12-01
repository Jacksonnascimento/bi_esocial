package com.recibos.recibos_service.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProcessadorEsocialLib {

    public String gerarScript(String caminhoPasta, String tipoEventoFiltro, String filtroCompetencia, int orgCod, int ambiente, int usrCod) {
        
        if (tipoEventoFiltro == null || tipoEventoFiltro.trim().isEmpty()) {
            tipoEventoFiltro = "T";
        }
        
        final String filtroFinal = tipoEventoFiltro;

        Map<String, InfoReciboDTO> dtosUnicos = new HashMap<>();
        ArquivoXML leitorXML = new ArquivoXML();
        GeradorSQLRecibo geradorSQL = new GeradorSQLRecibo();

        // 1. LEITURA (Varredura de arquivos XML e ZIP)
        try (Stream<Path> paths = Files.walk(Paths.get(caminhoPasta))) {
            
            paths.filter(Files::isRegularFile)
                 .forEach(path -> {
                     File arquivo = path.toFile();
                     String nomeArquivo = arquivo.getName().toLowerCase();

                     // --- CASO 1: ARQUIVO .XML ---
                     if (nomeArquivo.endsWith(".xml")) {
                         processarXmlIndividual(arquivo, filtroFinal, filtroCompetencia, leitorXML, dtosUnicos);
                     }
                     // --- CASO 2: ARQUIVO .ZIP ---
                     else if (nomeArquivo.endsWith(".zip")) {
                         processarZip(arquivo, filtroFinal, filtroCompetencia, leitorXML, dtosUnicos);
                     }
                 });

        } catch (IOException e) {
            return "-- ERRO AO ACESSAR PASTA: " + e.getMessage();
        }

        // 2. ESCRITA
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

        if (corpoScripts.length() == 0) {
            return "";
        }

        StringBuilder scriptFinal = new StringBuilder();
        scriptFinal.append("-- SCRIPT GERADO PELA BIBLIOTECA DE IMPORTAÇÃO DE RECIBOS\n");
        scriptFinal.append("-- Data/Hora Geração: ").append(java.time.LocalDateTime.now()).append("\n\n");
        scriptFinal.append(corpoScripts);

        return scriptFinal.toString();
    }

    // --- AUXILIAR: PROCESSA XML SOLTO ---
    private void processarXmlIndividual(File arquivo, String filtro, String competencia, ArquivoXML leitor, Map<String, InfoReciboDTO> mapa) {
        if (!filtro.equals("T") && !isArquivoCompativelComFiltro(arquivo.getName(), filtro)) return;

        try {
            InfoReciboDTO info = leitor.infXML(arquivo, arquivo.getName());
            adicionarSeValido(info, competencia, mapa);
        } catch (Exception e) {
            System.err.println("Erro ao ler XML " + arquivo.getName() + ": " + e.getMessage());
        }
    }

    // --- AUXILIAR: PROCESSA ZIP ---
    private void processarZip(File arquivoZip, String filtro, String competencia, ArquivoXML leitor, Map<String, InfoReciboDTO> mapa) {
        try (ZipFile zip = new ZipFile(arquivoZip)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String nomeEntry = entry.getName(); // Pode incluir pastas internas do zip
                
                // Pega apenas o nome do arquivo, sem pastas internas
                String nomeArquivoPuro = new File(nomeEntry).getName();

                if (!entry.isDirectory() && nomeEntry.toLowerCase().endsWith(".xml")) {
                    
                    if (!filtro.equals("T") && !isArquivoCompativelComFiltro(nomeArquivoPuro, filtro)) continue;

                    try (InputStream is = zip.getInputStream(entry)) {
                        // Passamos o InputStream do ZIP direto para o parser
                        InfoReciboDTO info = leitor.infXML(is, nomeArquivoPuro, arquivoZip.getAbsolutePath());
                        adicionarSeValido(info, competencia, mapa);
                    } catch (Exception e) {
                        System.err.println("Erro ao ler entrada " + nomeEntry + " no ZIP: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao abrir ZIP " + arquivoZip.getName() + ": " + e.getMessage());
        }
    }

    // --- LÓGICA DE VALIDAÇÃO E DEDUPLICAÇÃO ---
    private void adicionarSeValido(InfoReciboDTO info, String competencia, Map<String, InfoReciboDTO> mapa) {
        if (info.getId() == null && info.getRecibo() == null) return;

        if (competencia != null && !competencia.isEmpty()) {
            if (info.getPerApur() != null && !info.getPerApur().isEmpty()) {
                if (!info.getPerApur().equals(competencia)) return;
            }
        }

        String tipo = detectTipo(info.getNomeArquivo());
        String chave = info.getDeduplicationKey(tipo);
        
        InfoReciboDTO existente = mapa.get(chave);
        if (existente == null || info.isMaisRecenteQue(existente)) {
            mapa.put(chave, info);
        }
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
        
        return n.contains(f);
    }
    
    private String detectTipo(String nomeArquivo) {
        String n = nomeArquivo.toUpperCase();
        if (n.contains("2200") || n.contains("EVTADMISSAO")) return "S-2200";
        if (n.contains("2299") || n.contains("EVTDESLIG")) return "S-2299";
        if (n.contains("2300") || n.contains("EVTTSVINICIO")) return "S-2300";
        if (n.contains("1200") || n.contains("EVTREMUN")) return "S-1200";
        if (n.contains("1210") || n.contains("EVTPGTOS")) return "S-1210";
        if (n.contains("1202")) return "S-1202";
        return nomeArquivo; 
    }
}