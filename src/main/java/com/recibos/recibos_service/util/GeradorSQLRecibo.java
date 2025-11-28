package com.recibos.recibos_service.util;

public class GeradorSQLRecibo {

    /**
     * Gera o script SQL completo para um único arquivo XML processado.
     */
    public String gerarSQL(InfoReciboDTO info, String tipoEvento, int orgCod, int ambiente, int usrCod) {
        
        // Normaliza o tipo de evento
        String eventoLimpo = tipoEvento.replace(".xml", "").toUpperCase();
        
        switch (eventoLimpo) {
            case "2200":
            case "S-2200":
                return gerarScriptS2200(info, orgCod, ambiente, usrCod);
            
            // Futuros cases: 1200, 1210, etc.
            
            default:
                return "-- Evento " + tipoEvento + " não implementado nesta versão da biblioteca.\n";
        }
    }

    private String gerarScriptS2200(InfoReciboDTO info, int orgCod, int ambiente, int usrCod) {
        StringBuilder sql = new StringBuilder();
        
        // Tratamento de nulos
        String matricula = info.getMatricula() != null ? info.getMatricula() : "0";
        String cpf = info.getCpf() != null ? info.getCpf() : "0";
        String recibo = info.getRecibo();
        String idEvento = info.getId();
        String protocolo = info.getProtocolo() != null ? info.getProtocolo() : "";
        String nomeArquivo = info.getNomeArquivo();
        String caminhoArquivo = info.getCaminhoArquivo() != null ? info.getCaminhoArquivo().replace("\\", "\\\\") : "";
        
        // Extrai Data para o Envio
        String ano = "NULL";
        String mes = "NULL";
        if (info.getDhProcessamento() != null && info.getDhProcessamento().length() >= 7) {
            ano = info.getDhProcessamento().substring(0, 4);
            mes = info.getDhProcessamento().substring(5, 7);
        }

        // --- CABEÇALHO ---
        sql.append("/* \n");
        sql.append("   ARQUIVO: ").append(nomeArquivo).append("\n");
        sql.append("   EVENTO: S-2200 (Admissão)\n");
        sql.append("   MATRÍCULA: ").append(matricula).append("\n");
        sql.append("   CPF: ").append(cpf).append("\n");
        sql.append("*/\n\n");

        // --- BLOCO 1: ESOCIAL_CONTROLA_ENVIO ---
        sql.append("--------------------------------------------------------------------------------\n");
        sql.append("-- BLOCO 1: ATUALIZAÇÃO DA TABELA DE CONTROLE (ESOCIAL_CONTROLA_ENVIO)\n");
        sql.append("--------------------------------------------------------------------------------\n");
        
        sql.append("IF EXISTS (\n");
        sql.append("    SELECT 1 FROM ESOCIAL_CONTROLA_ENVIO ECE \n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF ON GF.FUN_COD = ECE.ECO_CHAVE \n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append(" \n");
        sql.append("      AND ECE.ETA_COD = 'S-2200'\n");
        sql.append("      AND ECE.ECO_AMBIENTE = ").append(ambiente).append("\n");
        sql.append(")\n");
        sql.append("BEGIN\n");
        sql.append("    UPDATE ESOCIAL_CONTROLA_ENVIO \n");
        sql.append("    SET ECO_RECIBO = '").append(recibo).append("',\n");
        sql.append("        ECO_SITUACAO = 'N'\n");
        sql.append("    FROM ESOCIAL_CONTROLA_ENVIO ECE\n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF ON GF.FUN_COD = ECE.ECO_CHAVE\n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append(" \n");
        sql.append("      AND ECE.ETA_COD = 'S-2200'\n");
        sql.append("      AND ECE.ECO_AMBIENTE = ").append(ambiente).append("\n");
        sql.append("END\n");
        sql.append("ELSE\n");
        sql.append("BEGIN\n");
        sql.append("    INSERT INTO ESOCIAL_CONTROLA_ENVIO \n");
        sql.append("    (ETA_COD, ECO_RECIBO, ECO_CHAVE, ECO_NOME_CHAVE, ECO_TIPO, ECO_SITUACAO, ECO_AMBIENTE, ORG_COD, ECO_CPF, ECO_NIS)\n");
        sql.append("    VALUES \n");
        sql.append("    ('S-2200', \n");
        sql.append("     '").append(recibo).append("', \n");
        sql.append("     (SELECT TOP 1 FUN_COD FROM GER_FUNCIONARIO WHERE FUN_MATRICULA = ").append(matricula).append("), \n");
        sql.append("     'FUN_COD', 'E', 'N', ").append(ambiente).append(", ").append(orgCod).append(", '").append(cpf).append("', '0')\n");
        sql.append("END;\n\n");

        // --- BLOCO 2: LOG E ENVIO ---
        sql.append("--------------------------------------------------------------------------------\n");
        sql.append("-- BLOCO 2: LOG E ENVIO (ESOCIAL_LOG_GER_FUNCIONARIO e ESOCIAL_ENVIO_DO_EVENTO)\n");
        sql.append("--------------------------------------------------------------------------------\n");
        
        sql.append("IF EXISTS (\n");
        sql.append("    SELECT 1 \n");
        sql.append("    FROM ESOCIAL_LOG_GER_FUNCIONARIO ELF WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF WITH(NOLOCK) ON GF.FUN_COD = ELF.FUN_COD\n");
        sql.append("    INNER JOIN ESOCIAL_ENVIO_DO_EVENTO EEE WITH(NOLOCK) ON EEE.ENV_COD = ELF.ENV_COD\n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append("\n");
        sql.append("      AND ELF.ESL_ORG_COD = ").append(orgCod).append("\n");
        sql.append("      AND EEE.ETA_COD = 'S-2200'\n");
        sql.append("      AND EEE.ENV_AMBIENTE = ").append(ambiente).append("\n");
        sql.append(")\n");
        sql.append("BEGIN\n");
        sql.append("    UPDATE ELF\n");
        sql.append("    SET ESL_NUMERO_RECIBO = '").append(recibo).append("',\n");
        sql.append("        ESL_ID_EVENTO = '").append(idEvento).append("'\n");
        sql.append("    FROM ESOCIAL_LOG_GER_FUNCIONARIO ELF\n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF ON GF.FUN_COD = ELF.FUN_COD\n");
        sql.append("    INNER JOIN ESOCIAL_ENVIO_DO_EVENTO EEE ON EEE.ENV_COD = ELF.ENV_COD\n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append("\n");
        sql.append("      AND ELF.ESL_ORG_COD = ").append(orgCod).append("\n");
        sql.append("      AND EEE.ETA_COD = 'S-2200'\n");
        sql.append("      AND EEE.ENV_AMBIENTE = ").append(ambiente).append("\n");
        sql.append("END\n");
        sql.append("ELSE\n");
        sql.append("BEGIN\n");
        
        // 1. Insert Envio
        sql.append("    INSERT INTO ESOCIAL_ENVIO_DO_EVENTO \n");
        sql.append("    (ETA_COD, USR_CODIGO, ENV_NOME_ARQUIVO, ENV_CAMINHO_ARQUIVO, ENV_PROTOCOLO, ENV_DATA_HORA, ENV_STATUS, ENV_MES_COD, ENV_ANO_COD, ENV_AMBIENTE, ORG_COD)\n");
        sql.append("    VALUES \n");
        sql.append("    ('S-2200', \n");
        sql.append("     ").append(usrCod).append(", \n");
        sql.append("     '").append(nomeArquivo).append("', \n");
        sql.append("     '").append(caminhoArquivo).append("', \n");
        sql.append("     '").append(protocolo).append("', \n");
        sql.append("     GETDATE(), 'P', ").append(mes).append(", ").append(ano).append(", ").append(ambiente).append(", ").append(orgCod).append(");\n\n");

        // 2. Insert Log
        sql.append("    INSERT INTO ESOCIAL_LOG_GER_FUNCIONARIO \n");
        sql.append("    (FUN_COD, ENV_COD, RES_COD, ESL_NUMERO_RECIBO, ESL_ORG_COD, ESL_ID_EVENTO)\n");
        sql.append("    VALUES \n");
        sql.append("    ((SELECT TOP 1 FUN_COD FROM GER_FUNCIONARIO WHERE FUN_MATRICULA = ").append(matricula).append("), \n");
        sql.append("     SCOPE_IDENTITY(), \n");
        sql.append("     7, \n");
        sql.append("     '").append(recibo).append("', \n");
        sql.append("     ").append(orgCod).append(", \n");
        sql.append("     '").append(idEvento).append("');\n");
        sql.append("END;\n\n");
        
        return sql.toString();
    }
}