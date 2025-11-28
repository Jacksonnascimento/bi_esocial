package com.recibos.recibos_service.controller;

import com.recibos.recibos_service.util.FonteDadoDTO;
import com.recibos.recibos_service.util.FonteDados;
import com.recibos.recibos_service.util.ProcessadorBatchService;
import com.recibos.recibos_service.util.LimpadorDePasta; 
import com.recibos.recibos_service.util.StatusSessaoService; 

import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriUtils; 

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api")
public class ReciboController {

    @Autowired
    private ProcessadorBatchService processadorBatchService;

    @Autowired
    private StatusSessaoService statusSessaoService; 

    private final Path DIRETORIO_BASE_DADOS = Paths.get("C:", "dados_app_recibos");
    private final Path uploadBaseDir = DIRETORIO_BASE_DADOS.resolve("recibos_uploads_pendentes");
    private final String pastaScripts = DIRETORIO_BASE_DADOS.resolve("scripts_gerados").toString();


    @PostMapping("/iniciar-processamento")
    public ResponseEntity<Map<String, String>> iniciarProcessamento() {
        try {
            new LimpadorDePasta(pastaScripts);
            System.out.println("Pasta 'scripts_gerados' limpa para nova sessão.");
            
            String sessaoId = UUID.randomUUID().toString();
            return ResponseEntity.ok(Map.of("sessaoId", sessaoId));

        } catch (Exception e) {
            System.err.println("Erro ao iniciar processamento: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("erro", "Erro ao iniciar sessão no servidor."));
        }
    }

    @PostMapping("/upload-lote")
    public ResponseEntity<String> uploadLote(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("sessaoId") String sessaoId) { 

        Path sessaoDir = uploadBaseDir.resolve(sessaoId);
        
        try {
            if (!Files.exists(sessaoDir)) {
                 Files.createDirectories(sessaoDir);
            }
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Erro ao criar diretório de sessão: " + e.getMessage());
        }

        int arquivosXmlSalvos = 0;

        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getOriginalFilename() == null) continue;
            
            String nomeOriginal = file.getOriginalFilename();
            if (!nomeOriginal.toLowerCase().endsWith(".xml")) continue; 
            
            try {
                String nomeLimpo = UriUtils.decode(nomeOriginal, "UTF-8");
                String nomeSeguro = nomeLimpo.substring(Math.max(nomeLimpo.lastIndexOf('/'), nomeLimpo.lastIndexOf('\\')) + 1);

                if (nomeSeguro.isEmpty()) continue; 

                File arquivoDestino = sessaoDir.resolve(nomeSeguro).toFile();
                
                if (arquivoDestino.exists()) {
                     arquivoDestino = sessaoDir.resolve(UUID.randomUUID().toString() + "_" + nomeSeguro).toFile();
                }

                file.transferTo(arquivoDestino);
                arquivosXmlSalvos++;

            } catch (IOException e) {
                System.err.println("Erro ao salvar ficheiro " + file.getOriginalFilename() + ": " + e.getMessage());
                return ResponseEntity.status(500).body("Erro ao salvar ficheiros. Verifique os logs.");
            }
        }
        
        return ResponseEntity.ok(arquivosXmlSalvos + " ficheiros .xml recebidos e salvos na sessão " + sessaoId);
    }
    
    
    @PostMapping("/processar-sessao")
    public ResponseEntity<String> processarSessao(@RequestBody Map<String, String> payload) {
        
        String sessaoId = payload.get("sessaoId");
        boolean modoInsert = Boolean.parseBoolean(payload.get("modoInsert"));
        String filtroPerApur = payload.get("filtroPerApur");
        String caminhoLocal = payload.get("caminhoLocal");
        
        // --- MUDANÇA: Recebe o filtro de evento ---
        String filtroTipoEvento = payload.get("filtroTipoEvento");


        if (sessaoId == null || sessaoId.isEmpty()) {
            return ResponseEntity.badRequest().body("sessaoId não fornecido.");
        }

        Path diretorioParaProcessar;
        boolean naoDeletarFonte = false;

        if (caminhoLocal != null && !caminhoLocal.isEmpty()) {
            System.out.println("Iniciando processamento de CAMINHO LOCAL: " + caminhoLocal);
            diretorioParaProcessar = Paths.get(caminhoLocal);
            naoDeletarFonte = true; 
            
            if (!Files.exists(diretorioParaProcessar) || !Files.isDirectory(diretorioParaProcessar)) {
                return ResponseEntity.status(400).body("Caminho local não encontrado ou não é um diretório: " + caminhoLocal);
            }
        } else {
            System.out.println("Iniciando processamento de SESSÃO UPLOAD: " + sessaoId);
            diretorioParaProcessar = uploadBaseDir.resolve(sessaoId);
            naoDeletarFonte = false; 
            
            if (!Files.exists(diretorioParaProcessar)) {
                 return ResponseEntity.status(400).body("ID de Sessão de upload não encontrado (provavelmente nenhum ficheiro foi enviado).");
            }
        }

        statusSessaoService.iniciarSessao(sessaoId);
        
        // --- MUDANÇA: Passa o filtro de evento para o serviço ---
        processadorBatchService.processarLote(sessaoId, diretorioParaProcessar, modoInsert, filtroPerApur, filtroTipoEvento, naoDeletarFonte);

        String resposta = "Processamento iniciado para a sessão " + sessaoId + ".";
        return ResponseEntity.accepted().body(resposta);
    }

    @GetMapping("/sessao-status")
    public ResponseEntity<StatusSessaoService.StatusSessao> getSessaoStatus(@RequestParam String sessaoId) {
        StatusSessaoService.StatusSessao status = statusSessaoService.getStatus(sessaoId);
        return ResponseEntity.ok(status);
    }

    // ... (Todos os outros endpoints de Download e Fontes de Dados permanecem exatamente iguais) ...
    
    @GetMapping("/download/gerados/completo")
    public ResponseEntity<Resource> downloadArquivosGeradosCompleto() throws IOException {
        File pastaScriptsFile = new File(pastaScripts);
        if (!pastaScriptsFile.exists() || !pastaScriptsFile.isDirectory()) {
            return ResponseEntity.status(404).body(null);
        }
        File[] arquivosSql = pastaScriptsFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        if (arquivosSql == null || arquivosSql.length == 0) {
            String aviso = "-- Nenhum script .sql encontrado na pasta " + pastaScripts + ".\n";
            byte[] bytes = aviso.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scripts_vazio.sql\"")
                    .contentLength(bytes.length)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        }
        Arrays.sort(arquivosSql); 
        StringBuilder conteudoCompleto = new StringBuilder();
        conteudoCompleto.append("-- SCRIPT CONSOLIDADO - SISTEMA DE IMPORTAÇÃO\n");
        conteudoCompleto.append("-- Total de ficheiros processados: ").append(arquivosSql.length).append("\n\n");
        for (File file : arquivosSql) {
            try {
                String conteudo = Files.readString(file.toPath());
                conteudoCompleto.append("-- Ficheiro: ").append(file.getName()).append("\n");
                conteudoCompleto.append(conteudo).append("\n\n");
            } catch (IOException e) {
                System.err.println("Erro ao ler ficheiro " + file.getName() + ": " + e.getMessage());
            }
        }
        byte[] bytes = conteudoCompleto.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scripts_consolidado.sql\"")
                .contentLength(bytes.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/download/{nomeArquivo}")
    public ResponseEntity<Resource> downloadArquivo(@PathVariable String nomeArquivo) throws IOException {
        File file = new File(pastaScripts, nomeArquivo);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toURI());
        String contentDisposition = "attachment; filename=\"" + file.getName() + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    @GetMapping("/download-multiple")
    public ResponseEntity<StreamingResponseBody> downloadMultiple(@RequestParam List<String> arquivos) {
        StreamingResponseBody stream = outputStream -> {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                for (String nomeArquivo : arquivos) {
                    File file = new File(pastaScripts, nomeArquivo);
                    if (file.exists()) {
                        zos.putNextEntry(new ZipEntry(file.getName()));
                        try (InputStream fis = new FileInputStream(file)) {
                            fis.transferTo(zos);
                        }
                        zos.closeEntry();
                    }
                }
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=arquivos.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    @GetMapping("/download/completo")
    public ResponseEntity<Resource> downloadArquivoCompleto(@RequestParam List<String> arquivos) throws IOException {
        if (arquivos == null || arquivos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        StringBuilder conteudoCompleto = new StringBuilder();
        for (String nomeArquivo : arquivos) {
            File file = new File(pastaScripts, nomeArquivo);
            if (file.exists()) {
                String conteudo = Files.readString(file.toPath());
                conteudoCompleto.append("-- Início do script: ").append(nomeArquivo).append("\n");
                conteudoCompleto.append(conteudo).append("\n\n");
            }
        }
        byte[] bytes = conteudoCompleto.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scripts_completos.sql\"")
                .contentLength(bytes.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/fontes-dados")
    public ResponseEntity<List<FonteDadoDTO>> listarFontesDados() {
        try {
            FonteDados fonteDados = new FonteDados();
            List<FonteDadoDTO> lista = fonteDados.listarFontesDados();
            return ResponseEntity.ok(lista);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/fontes-dados/editar")
    public ResponseEntity<Void> editarFonteDado(@RequestBody Map<String, String> payload) {
        String nomeArquivo = payload.get("nomeArquivo");
        String conteudo = payload.get("conteudo");
        try {
            FonteDados fonteDados = new FonteDados();
            if (fonteDados.getArquivoPorNome(nomeArquivo) == null) {
                return ResponseEntity.notFound().build();
            }
            fonteDados.escreverArquivoPorNome(nomeArquivo, conteudo);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}