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
     * Varre a pasta, processa os XMLs e retorna um único script SQL concatenado.
     *
     * @param caminhoPasta       Diretório raiz para busca recursiva.
     * @param tipoEventoFiltro   Ex: "S-2200", "S-1200" ou "T" (Todos).
     * @param filtroCompetencia  Filtro de Período (ex: "2023-01"). Aplica-se apenas a eventos periódicos.
     * @param orgCod             Código da Organização (parâmetro externo).
     * @param ambiente           Ambiente (1-Prod, 2-Teste).
     * @param usrCod             Código do Usuário.
     * @return String contendo todos os comandos SQL gerados.
     */
    public String gerarScript(String caminhoPasta, String tipoEventoFiltro, String filtroCompetencia, int orgCod, int ambiente, int usrCod) {
        
        StringBuilder scriptFinal = new StringBuilder();
        scriptFinal.append("-- SCRIPT GERADO PELA BIBLIOTECA DE IMPORTAÇÃO DE RECIBOS\n");
        scriptFinal.append("-- Data/Hora Geração: ").append(java.time.LocalDateTime.now()).append("\n\n");

        // Mapa para Deduplicação: Chave -> DTO
        Map<String, InfoReciboDTO> dtosUnicos = new HashMap<>();
        
        // Auxiliares
        ArquivoXML leitorXML = new ArquivoXML();
        GeradorSQLRecibo geradorSQL = new GeradorSQLRecibo();

        // 1. Varredura e Parsing (ETAPA DE LEITURA)
        try (Stream<Path> paths = Files.walk(Paths.get(caminhoPasta))) {
            
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                 .forEach(path -> {
                     File arquivo = path.toFile();
                     String nomeArquivo = arquivo.getName();

                     // --- Filtro de Tipo de Arquivo (Nome) ---
                     // Se não for "T" (Todos) e o nome do arquivo não contiver o filtro, ignora.
                     if (tipoEventoFiltro != null && !tipoEventoFiltro.equalsIgnoreCase("T")) {
                         // Remove "S-" ou "." para comparação flexível (ex: filtro "S-2200" bate com arquivo "evtAdmissao...xml"?)
                         // A melhor forma aqui é confiar no mapeamento interno ou numa verificação simples de string se o arquivo não tem o código no nome.
                         // Assumindo que o usuário vai passar algo como "2200" ou "S-2200" e o arquivo tem algo identificável ou aceitamos tudo e filtramos depois do parse.
                         
                         // Estratégia Híbrida: Tenta filtrar por nome se possível, senão filtra pelo ID da tag depois.
                         // Para simplificar: Vamos ler todos e checar o tipo depois do parse, ou assumir que o filtro é parte do nome (ex: filtro "2200" pega arquivos com "2200" no nome?)
                         // O seu sistema antigo filtrava pelo final do nome. Vamos manter simples:
                         // Se filtro for "S-2200", aceita arquivos que contenham "2200".
                         
                         String filtroLimpo = tipoEventoFiltro.replace("S-", "").replace(".xml", "");
                         // Mapeamento simples de códigos comuns para nomes de arquivo padrão eSocial (opcional, mas ajuda performance)
                         boolean pareceCompativel = nomeArquivo.contains(filtroLimpo);
                         
                         // Se quiser ser estrito com nomes padronizados, pode descomentar:
                         /* if (!pareceCompativel) return; 
                         */
                     }

                     try {
                         // Faz o Parse
                         // O segundo parâmetro "tipoEvento" ajuda o parser a achar a tag certa.
                         // Se o filtro for "T", tentamos adivinhar pelo nome do arquivo.
                         String tipoEventoParaParser = nomeArquivo; 
                         
                         InfoReciboDTO info = leitorXML.infXML(arquivo, tipoEventoParaParser);
                         
                         // Se o parser não achou ID nem CPF/Matrícula, provavelmente não era um XML de evento válido ou o tipo estava errado.
                         if (info.getId() == null && info.getRecibo() == null) {
                             return; 
                         }
                         
                         // --- Filtro de Tipo de Evento (Pós-Parse) ---
                         // Garante que só processamos o que foi pedido (ex: pediu S-2200, garante que é tag evtAdmissao ou similar)
                         // (Implementação simplificada: confia no filtro de entrada ou assume que a pasta tem os arquivos certos)

                         // --- Filtro de Competência (Fase 3) ---
                         if (filtroCompetencia != null && !filtroCompetencia.isEmpty()) {
                             // Só aplica filtro se o XML tiver perApur (ou seja, for periódico)
                             if (info.getPerApur() != null && !info.getPerApur().isEmpty()) {
                                 if (!info.getPerApur().equals(filtroCompetencia)) {
                                     return; // Ignora competências diferentes
                                 }
                             }
                         }

                         // --- Deduplicação ---
                         // Usa o nome do arquivo ou um identificador fixo como "tipo" para a chave
                         // Para o S-2200, usamos "2200".
                         String tipoEventoDetectado = detectarTipoEvento(nomeArquivo); 
                         String chave = info.getDeduplicationKey(tipoEventoDetectado);
                         
                         InfoReciboDTO existente = dtosUnicos.get(chave);
                         
                         if (existente == null || info.isMaisRecenteQue(existente)) {
                             dtosUnicos.put(chave, info);
                         }

                     } catch (Exception e) {
                         System.err.println("Erro ao ler arquivo " + nomeArquivo + ": " + e.getMessage());
                         // Continua o loop, não para a biblioteca por um arquivo ruim
                     }
                 });

        } catch (IOException e) {
            return "-- ERRO CRÍTICO AO ACESSAR A PASTA: " + e.getMessage();
        }

        // 2. Ordenação e Geração de Script (ETAPA DE ESCRITA)
        // Usamos um TreeMap para ordenar os arquivos processados por nome ou chave, para o script sair organizado
        // Map<ChaveOrdenacao, SQL>
        
        if (dtosUnicos.isEmpty()) {
            return "-- Nenhum arquivo encontrado ou válido para os filtros informados.";
        }

        // Gera o SQL para cada item único
        for (InfoReciboDTO info : dtosUnicos.values()) {
             String tipo = detectarTipoEvento(info.getNomeArquivo());
             
             // Filtro Final de Segurança: Se o usuário pediu S-2200, garante que só gera S-2200
             if (tipoEventoFiltro != null && !tipoEventoFiltro.equals("T")) {
                 String filtroLimpo = tipoEventoFiltro.replace("S-", "").replace(".xml", "");
                 if (!tipo.contains(filtroLimpo)) continue;
             }

             String sql = geradorSQL.gerarSQL(info, tipo, orgCod, ambiente, usrCod);
             scriptFinal.append(sql);
        }

        return scriptFinal.toString();
    }
    
    // Auxiliar simples para classificar o evento baseando-se no nome do arquivo (padrão comum)
    private String detectarTipoEvento(String nomeArquivo) {
        if (nomeArquivo.contains("2200")) return "S-2200";
        if (nomeArquivo.contains("2299")) return "S-2299";
        if (nomeArquivo.contains("2300")) return "S-2300";
        if (nomeArquivo.contains("1200")) return "S-1200";
        if (nomeArquivo.contains("1210")) return "S-1210";
        if (nomeArquivo.contains("1202")) return "S-1202";
        return nomeArquivo; // Retorna o nome se não identificar padrão
    }
}