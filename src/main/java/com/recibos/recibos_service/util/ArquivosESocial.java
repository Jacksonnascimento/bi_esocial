package com.recibos.recibos_service.util;

import java.io.IOException;
import java.net.URISyntaxException;

public class ArquivosESocial {

    FonteDados fonte;

    public ArquivosESocial(boolean insert) throws URISyntaxException {
        try {
            fonte = new FonteDados();
        } catch (IOException e) {
            e.printStackTrace();
           
        }

        fonte.iniciarCaminhodosEventos(insert);
    
    }

    public String s2200(String matricula, String recibo)
            throws IOException {
        String update = String.format(fonte.getEventoS2200(), recibo, matricula);

        return update;
    }

    public String s2299(String matricula, String recibo)
            throws IOException {
        String update = String.format(fonte.getEventoS2299(), recibo, matricula);

        return update;

    }

    // --- NOVO MÉTODO ADICIONADO PARA S-2300 ---
    public String s2300(String matricula, String recibo)
            throws IOException {
        String update = String.format(fonte.getEventoS2300(), recibo, matricula);

        return update;
    }
    // --- FIM DO NOVO MÉTODO ---

    public String s1200(String cpf, String recibo, String perApur) throws IOException {
        String update = String.format(fonte.getEventosTerceiraFase(), recibo, "S-1200", cpf, perApur, perApur);

        return update;

    }

    // NOVO MÉTODO ADICIONADO:
    public String s1202(String cpf, String recibo, String perApur) throws IOException {
        // Reutiliza o mesmo template do S-1200, apenas muda o código do evento
        String update = String.format(fonte.getEventosTerceiraFase(), recibo, "S-1202", cpf, perApur, perApur);

        return update;
    }


    public String s1210(String cpf, String recibo, String perApur) throws IOException {
        String update = String.format(fonte.getEventosTerceiraFase(), recibo, "S-1210", cpf, perApur, perApur);

        return update;

    }

    public String s3000(String recibo) throws IOException {
        String update = String.format(fonte.getEventoS3000(), recibo);

        return update;

    }

}