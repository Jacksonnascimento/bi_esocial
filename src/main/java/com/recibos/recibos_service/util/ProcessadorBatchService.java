package com.recibos.recibos_service.util;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired; 

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList; 
import java.util.HashMap;   
import java.util.List;      
import java.util.Map;       
import java.util.stream.Stream; // Importar java.util.stream.Stream

@Service
public class ProcessadorBatchService {

    @Autowired
    private StatusSessaoService statusSessaoService;

    private final Path DIRETORIO_BASE_DADOS = Paths.get("C:", "dados_app_recibos");
    private final String pastaScripts = DIRETORIO_BASE_DADOS.resolve("scripts_gerados").toString();


    private static class DtoWrapper {
        final InfoReciboDTO info;
        final String tipoEvento;
        final String nomeOriginal;

        DtoWrapper(InfoReciboDTO info, String tipoEvento, String nomeOriginal) { 
            this.info = info;
            this.tipoEvento = tipoEvento;
            this.nomeOriginal = nomeOriginal;
        }
    }

    /**
     * --- MUDANÇA: Assinatura alterada ---
     * @param sessaoId O ID para tracking do status
     * @param diretorioParaProcessar O Path para ler
     * @param filtroTipoEvento O filtro de evento (ex: "1200.xml" ou "")
     * @param naoDeletarFonte true se for um caminho local
     */
    @Async
    public void processarLote(String sessaoId, Path diretorioParaProcessar, boolean modoInsert, String filtroPerApur, String filtroTipoEvento, boolean naoDeletarFonte) {
        
        int scriptsGerados = 0;
        
        try {
            System.out.println("Processando SESSÃO " + sessaoId + " | Fonte: " + diretorioParaProcessar);
            
            GeradorSQLRecibo geradorSQL = new GeradorSQLRecibo(modoInsert); 
            ArquivoXML leitorXML = new ArquivoXML();
            Map<String, DtoWrapper> dtosMaisRecentes = new HashMap<>();
            List<File> arquivosParaDeletar = new ArrayList<>(); 

            System.out.println("Sessão " + sessaoId + ": Iniciando Etapa 1 (Parse, Filtro e Deduplicação)...");
            
            // --- MUDANÇA APLICADA: 'Files.list' substituído por 'Files.walk' ---
            // --- E adicionado .filter(Files::isRegularFile) ---
            try (Stream<Path> stream = Files.walk(diretorioParaProcessar)) {
                stream.filter(Files::isRegularFile).forEach(arquivoPath -> {
                    File arquivoTemp = arquivoPath.toFile();
                    String nomeOriginal = arquivoTemp.getName();
                    
                    if (!naoDeletarFonte) {
                        // Se não for modo "caminho local", marca para deletar (lógica de upload)
                        // Apenas adiciona arquivos, não o diretório raiz
                        if(!arquivoTemp.isDirectory()) {
                            arquivosParaDeletar.add(arquivoTemp); 
                        }
                    }

                    try {
                        // --- MUDANÇA: FILTRO DE EVENTO (SERVER-SIDE) ---
                        // Isto agora funciona para o "Modo Caminho Local"
                        if (filtroTipoEvento != null && !filtroTipoEvento.isEmpty()) {
                            if (!nomeOriginal.endsWith(filtroTipoEvento)) {
                                return; // Salta este ficheiro, não corresponde ao filtro de evento
                            }
                        }
                        // --- FIM DA MUDANÇA ---
                        
                        // O ficheiro passou no filtro de evento (ou não há filtro)
                        
                        String tipoEvento = nomeOriginal.substring(nomeOriginal.length() - 8);
                        InfoReciboDTO info = leitorXML.infXML(arquivoTemp, tipoEvento);

                        // Filtro de Competência (perApur)
                        if (filtroPerApur != null && !filtroPerApur.isEmpty()) {
                            if ("1200.xml".equals(tipoEvento) || "1202.xml".equals(tipoEvento) || "1210.xml".equals(tipoEvento)) {
                                if (info.getPerApur() == null || !info.getPerApur().equals(filtroPerApur)) {
                                    return; // Salta este ficheiro, não corresponde ao filtro de competência
                                }
                            }
                        }

                        // Deduplicação
                        String chave = info.getDeduplicationKey(tipoEvento);
                        DtoWrapper wrapperNovo = new DtoWrapper(info, tipoEvento, nomeOriginal);
                        DtoWrapper wrapperExistente = dtosMaisRecentes.get(chave);

                        if (wrapperExistente == null || wrapperNovo.info.isMaisRecenteQue(wrapperExistente.info)) {
                            dtosMaisRecentes.put(chave, wrapperNovo);
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao fazer parse do arquivo " + nomeOriginal + ": " + e.getMessage());
                    }
                });
            }
            
            System.out.println("Sessão " + sessaoId + ": Etapa 1 concluída. " + dtosMaisRecentes.size() + " recibos únicos selecionados.");
            
            System.out.println("Sessão " + sessaoId + ": Iniciando Etapa 2 (Geração de SQL)...");

            for (DtoWrapper wrapper : dtosMaisRecentes.values()) {
                try {
                    String scriptSQL = geradorSQL.gerarSQL(wrapper.info, wrapper.tipoEvento);
                    salvarScript(scriptSQL, wrapper.nomeOriginal);
                    scriptsGerados++; 
                    System.out.println("SQL gerado para: " + wrapper.nomeOriginal);
                } catch (Exception e) {
                    System.err.println("Erro ao gerar SQL para " + wrapper.nomeOriginal + ": " + e.getMessage());
                }
            }
            
            System.out.println("Sessão " + sessaoId + ": Etapa 2 concluída.");
            
            if (!naoDeletarFonte) {
                System.out.println("Sessão " + sessaoId + ": Iniciando Etapa 3 (Limpeza da sessão de upload)...");
                for(File f : arquivosParaDeletar) {
                    f.delete();
                }
                Files.delete(diretorioParaProcessar); // Deleta a pasta da sessão
            } else {
                 System.out.println("Sessão " + sessaoId + ": Ignorando limpeza (Modo Caminho Local).");
            }
            
            System.out.println("Processamento da SESSÃO " + sessaoId + " concluído.");
            statusSessaoService.completarSessao(sessaoId, scriptsGerados);

        } catch (Exception e) {
            System.err.println("Erro grave no processamento da SESSÃO " + sessaoId + ": " + e.getMessage());
            statusSessaoService.falharSessao(sessaoId, e.getMessage());
            e.printStackTrace();
            
            if (!naoDeletarFonte) {
                try {
                    // Tenta limpar o diretório da sessão em caso de falha
                    Files.walk(diretorioParaProcessar)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (IOException ioe) {
                    System.err.println("Erro ao limpar diretório de sessão com falha: " + sessaoId);
                }
            }
        }
    }

    private String salvarScript(String script, String nomeArquivoOriginal) throws IOException {
        File dir = new File(pastaScripts);
        if (!dir.exists())
            dir.mkdirs();

        String nomeScript = nomeArquivoOriginal.replaceAll("\\.xml$", ".sql");
        File arquivoScript = new File(dir, nomeScript);

        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(arquivoScript))) {
            bw.write(script);
        }
        return arquivoScript.getAbsolutePath();
    }
}