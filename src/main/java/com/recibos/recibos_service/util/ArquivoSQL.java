package com.recibos.recibos_service.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ArquivoSQL {

    public static void salvarSQL(String conteudoSQL, String nomeArquivo) {
        try {
            String pasta = "scripts_gerados";
            File dir = new File(pasta);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, nomeArquivo);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(conteudoSQL);
            }

            
        } catch (IOException e) {
             e.printStackTrace();
        }
    }
}
