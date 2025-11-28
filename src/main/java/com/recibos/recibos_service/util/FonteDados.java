package com.recibos.recibos_service.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FonteDados {

    // --- MUDANÇA: CAMINHO ABSOLUTO E ESTÁVEL ---
    private static final String DIRETORIO_BASE_DADOS = "C:" + File.separator + "dados_app_recibos";

    // Aponta a pasta de configuração para dentro do diretório-mãe
    private static final String PASTA_BASE = DIRETORIO_BASE_DADOS + File.separator + "arquivos" + File.separator + "recibos-fontes-dados";
    
    private static final String PASTA_CONFIG = PASTA_BASE + File.separator + "config" + File.separator
            + "fontes de dados";

    // --- MUDANÇA: Adicionados eventos S-2300 ---
    private static final List<String> NOMES_ARQUIVOS = Arrays.asList(
            "eventoS2200.txt",
            "eventoS2200_insert.txt",
            "eventoS2299.txt",
            "eventoS2299_insert.txt",
            "eventoS2300.txt", // ADICIONADO
            "eventoS2300_insert.txt", // ADICIONADO
            "eventoS3000.txt",
            "eventoS3000_insert.txt",
            "eventosTerceiraFase.txt",
            "eventosTerceiraFase_insert.txt");

    private final Map<String, File> arquivos = new HashMap<>();

    public FonteDados() throws IOException {
        criarPastaEArquivosSeNaoExistirem();
    }

    private void criarPastaEArquivosSeNaoExistirem() throws IOException {
        File pastaConfig = new File(PASTA_CONFIG);
        if (!pastaConfig.exists()) {
            boolean criada = pastaConfig.mkdirs();
            if (!criada) {
                throw new IOException("❌ Não foi possível criar a pasta: " + PASTA_CONFIG);
            }
        }

        for (String nomeArquivo : NOMES_ARQUIVOS) {
            File arquivo = new File(pastaConfig, nomeArquivo);
            if (!arquivo.exists()) {
                boolean criado = arquivo.createNewFile();
                if (!criado) {
                    throw new IOException("❌ Não foi possível criar o arquivo: " + arquivo.getAbsolutePath());
                }
            }
            arquivos.put(nomeArquivo, arquivo);
        }
    }

    // Campos dos arquivos
    private File arquivoEventosTerceiraFase;
    private File arquivoS2200;
    private File arquivoS2299;
    private File arquivoS2300; // ADICIONADO
    private File arquivoS3000;

    public void iniciarCaminhodosEventos(boolean insert) {
        setCampoArquivo("eventosTerceiraFase", buscarArquivo(insert, "eventosTerceiraFase"));
        setCampoArquivo("eventoS2200", buscarArquivo(insert, "eventoS2200"));
        setCampoArquivo("eventoS2299", buscarArquivo(insert, "eventoS2299"));
        setCampoArquivo("eventoS2300", buscarArquivo(insert, "eventoS2300")); // ADICIONADO
        setCampoArquivo("eventoS3000", buscarArquivo(insert, "eventoS3000"));
    }

    private File buscarArquivo(boolean insert, String base) {
        String nome = insert ? base + "_insert.txt" : base + ".txt";
        return arquivos.get(nome);
    }

    private void setCampoArquivo(String nomeBase, File arquivo) {
        switch (nomeBase) {
            case "eventosTerceiraFase" -> this.arquivoEventosTerceiraFase = arquivo;
            case "eventoS2200" -> this.arquivoS2200 = arquivo;
            case "eventoS2299" -> this.arquivoS2299 = arquivo;
            case "eventoS2300" -> this.arquivoS2300 = arquivo; // ADICIONADO
            case "eventoS3000" -> this.arquivoS3000 = arquivo;
        }
    }

    // Métodos get/set
    public String getEventosTerceiraFase() throws IOException {
        return lerArquivo(arquivoEventosTerceiraFase);
    }

    public void setEventosTerceiraFase(String fonte) throws IOException {
        escreverArquivo(arquivoEventosTerceiraFase, fonte);
    }

    public String getEventoS2200() throws IOException {
        return lerArquivo(arquivoS2200);
    }

    public void setEventoS2200(String fonte) throws IOException {
        escreverArquivo(arquivoS2200, fonte);
    }

    public String getEventoS2299() throws IOException {
        return lerArquivo(arquivoS2299);
    }

    public void setEventoS2299(String fonte) throws IOException {
        escreverArquivo(arquivoS2299, fonte);
    }

    // --- MÉTODOS ADICIONADOS PARA S-2300 ---
    public String getEventoS2300() throws IOException {
        return lerArquivo(arquivoS2300);
    }

    public void setEventoS2300(String fonte) throws IOException {
        escreverArquivo(arquivoS2300, fonte);
    }
    // --- FIM DOS MÉTODOS ADICIONADOS ---

    public String getEventoS3000() throws IOException {
        return lerArquivo(arquivoS3000);
    }

    public void setEventoS3000(String fonte) throws IOException {
        escreverArquivo(arquivoS3000, fonte);
    }

    // Métodos de leitura/escrita
    private String lerArquivo(File arquivo) throws IOException {
        if (arquivo == null || !arquivo.exists()) {
            throw new FileNotFoundException(
                    "Arquivo não encontrado: " + (arquivo == null ? "null" : arquivo.getAbsolutePath()));
        }
        return Files.readString(arquivo.toPath()).trim();
    }

    public void escreverArquivo(File arquivo, String conteudo) throws IOException {
        if (arquivo == null) {
            throw new IOException("Arquivo destino não definido");
        }
        Files.writeString(arquivo.toPath(), conteudo + System.lineSeparator(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    // Métodos para o editor de Fontes de Dados
    public List<FonteDadoDTO> listarFontesDados() throws IOException {
        List<FonteDadoDTO> lista = new ArrayList<>();
        for (Map.Entry<String, File> entry : arquivos.entrySet()) {
            String conteudo = lerArquivo(entry.getValue());
            lista.add(new FonteDadoDTO(entry.getKey(), conteudo));
        }
        return lista;
    }

    public File getArquivoPorNome(String nomeArquivo) {
        return arquivos.get(nomeArquivo);
    }

    public void escreverArquivoPorNome(String nomeArquivo, String conteudo) throws IOException {
        File arquivo = arquivos.get(nomeArquivo);
        escreverArquivo(arquivo, conteudo);
    }
}