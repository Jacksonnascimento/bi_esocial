package com.recibos.recibos_service;

import com.recibos.recibos_service.util.ProcessadorEsocialLib;
import java.io.File;

public class TesteBiblioteca {

    public static void main(String[] args) {
        // --- CONFIGURAÇÃO DO TESTE ---
        // Ajuste este caminho para uma pasta que exista no seu PC e tenha XMLs
        String pastaTeste = "C:\\Users\\jacks\\Downloads"; 
        
        // Parâmetros de simulação
        int orgCod = 1;
        int ambiente = 1; // 1-Produção
        int usrCod = 157;
        String filtroEvento = "T"; 
        
        System.out.println("=== INICIANDO TESTE DA BIBLIOTECA ===");
        System.out.println("Lendo pasta: " + pastaTeste);

        // 1. Garante que a pasta existe (opcional, só pra não dar erro feio)
        File dir = new File(pastaTeste);
        if (!dir.exists()) {
            System.err.println("ERRO: A pasta " + pastaTeste + " não existe!");
            System.err.println("Crie a pasta e coloque um XML do S-2200 dentro dela para testar.");
            return;
        }

        // 2. Instancia a classe principal da nossa biblioteca
        ProcessadorEsocialLib biblioteca = new ProcessadorEsocialLib();

        // 3. Chama o método gerador
        // Assinatura: (caminho, tipoEvento, filtroCompetencia, org, amb, usr)
        String sqlGerado = biblioteca.gerarScript(
                pastaTeste, 
                filtroEvento, 
                null,       // Competência (null pois S-2200 não tem perApur)
                orgCod, 
                ambiente, 
                usrCod
        );

        // 4. Mostra o resultado no console
        
        System.out.println(sqlGerado);
      
        
        // Dica: Se quiser salvar em arquivo para conferir:
        /*
        try {
            Files.write(Paths.get("C:\\xmls_para_teste\\script_saida.sql"), sqlGerado.getBytes());
            System.out.println("Arquivo salvo em: C:\\xmls_para_teste\\script_saida.sql");
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
    }
}