package com.recibos.recibos_service.util;

import java.io.File;

public class LimpadorDePasta {

    public LimpadorDePasta(String caminhoPasta) {
        File pasta = new File(caminhoPasta);

        if (pasta.exists() && pasta.isDirectory()) {
            File[] arquivos = pasta.listFiles();
            if (arquivos != null) {
                for (File arquivo : arquivos) {
                    if (arquivo.isFile()) {
                        arquivo.delete();
                    }
                }
            }
        } 
    }
}
