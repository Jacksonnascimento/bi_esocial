package com.recibos.recibos_service.util;

public class GeradorSQLRecibo {

    /**
     * Gera o script SQL completo para um único arquivo XML processado.
     * Retorna NULL se não houver recibo válido.
     */
    public String gerarSQL(InfoReciboDTO info, String tipoEvento, int orgCod, int ambiente, int usrCod) {
        
        // --- TRAVA DE SEGURANÇA 1: SEM RECIBO = SEM SCRIPT ---
        if (info.getRecibo() == null || info.getRecibo().trim().isEmpty()) {
            return null;
        }

        // Normaliza o tipo de evento (remove .xml e S- inicial)
        String eventoLimpo = tipoEvento.replace(".xml", "").toUpperCase();
        if (eventoLimpo.startsWith("S-")) {
            eventoLimpo = eventoLimpo.substring(2);
        }
        
        switch (eventoLimpo) {
            // --- FASE 2: VÍNCULO VIA MATRÍCULA ---
            case "2200": // Admissão
            case "2300": // TSVE Início
            case "2299": // Desligamento
                return gerarScriptFase2(info, "S-" + eventoLimpo, orgCod, ambiente, usrCod);
            
            // --- FASE 3: PERIÓDICOS VIA CPF ---
            case "1200": // Remuneração
            case "1210": // Pagamentos
            case "1202": // Remuneração RPPS
                return gerarScriptFase3(info, "S-" + eventoLimpo, orgCod, ambiente, usrCod);
            
            default:
                return null; 
        }
    }

    /**
     * GERAÇÃO FASE 2 (S-2200, S-2300, S-2299)
     * Chave: GER_FUNCIONARIO (Matrícula)
     */
    private String gerarScriptFase2(InfoReciboDTO info, String codEvento, int orgCod, int ambiente, int usrCod) {
        StringBuilder sql = new StringBuilder();
        
        String matricula = info.getMatricula() != null ? info.getMatricula() : "0";
        String cpf = info.getCpf() != null ? info.getCpf() : "0";
        String recibo = info.getRecibo();
        String idEvento = info.getId();
        String protocolo = info.getProtocolo() != null ? info.getProtocolo() : "";
        String nomeArquivo = info.getNomeArquivo();
        String caminhoArquivo = info.getCaminhoArquivo() != null ? info.getCaminhoArquivo().replace("\\", "\\\\") : "";
        
        String anoProc = "NULL";
        String mesProc = "NULL";
        if (info.getDhProcessamento() != null && info.getDhProcessamento().length() >= 7) {
            anoProc = info.getDhProcessamento().substring(0, 4);
            mesProc = info.getDhProcessamento().substring(5, 7);
        }

        sql.append("/* \n");
        sql.append("   ARQUIVO: ").append(nomeArquivo).append("\n");
        sql.append("   EVENTO: ").append(codEvento).append(" (Fase 2)\n");
        sql.append("   MATRÍCULA: ").append(matricula).append("\n");
        sql.append("*/\n\n");

        // BLOCO 1: CONTROLA ENVIO
        sql.append("--------------------------------------------------------------------------------\n");
        sql.append("-- BLOCO 1: ATUALIZAÇÃO DA TABELA DE CONTROLE\n");
        sql.append("--------------------------------------------------------------------------------\n");
        
        sql.append("IF EXISTS (\n");
        sql.append("    SELECT 1 FROM ESOCIAL_CONTROLA_ENVIO ECE WITH(NOLOCK) \n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF WITH(NOLOCK) ON GF.FUN_COD = ECE.ECO_CHAVE \n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append(" \n");
        sql.append("      AND ECE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND ECE.ECO_AMBIENTE = ").append(ambiente).append("\n");
        sql.append(")\n");
        sql.append("BEGIN\n");
        sql.append("    UPDATE ESOCIAL_CONTROLA_ENVIO \n");
        sql.append("    SET ECO_RECIBO = '").append(recibo).append("',\n");
        sql.append("        ECO_SITUACAO = 'N'\n");
        sql.append("    FROM ESOCIAL_CONTROLA_ENVIO ECE WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF WITH(NOLOCK) ON GF.FUN_COD = ECE.ECO_CHAVE\n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append(" \n");
        sql.append("      AND ECE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND ECE.ECO_AMBIENTE = ").append(ambiente).append("\n");
        sql.append("END\n");
        sql.append("ELSE\n");
        sql.append("BEGIN\n");
        // --- TRAVA DE SEGURANÇA ---
        sql.append("    IF EXISTS (SELECT 1 FROM GER_FUNCIONARIO WITH(NOLOCK) WHERE FUN_MATRICULA = ").append(matricula).append(")\n");
        sql.append("    BEGIN\n");
        sql.append("        INSERT INTO ESOCIAL_CONTROLA_ENVIO \n");
        sql.append("        (ETA_COD, ECO_RECIBO, ECO_CHAVE, ECO_NOME_CHAVE, ECO_TIPO, ECO_SITUACAO, ECO_AMBIENTE, ORG_COD, ECO_CPF, ECO_NIS)\n");
        sql.append("        VALUES \n");
        sql.append("        ('").append(codEvento).append("', \n");
        sql.append("         '").append(recibo).append("', \n");
        sql.append("         (SELECT TOP 1 FUN_COD FROM GER_FUNCIONARIO WITH(NOLOCK) WHERE FUN_MATRICULA = ").append(matricula).append("), \n");
        sql.append("         'FUN_COD', 'E', 'N', ").append(ambiente).append(", ").append(orgCod).append(", '").append(cpf).append("', '0')\n");
        sql.append("    END\n");
        sql.append("END;\n\n");

        // BLOCO 2: LOG E ENVIO
        sql.append("--------------------------------------------------------------------------------\n");
        sql.append("-- BLOCO 2: LOG E ENVIO (LOG DE FUNCIONÁRIO)\n");
        sql.append("--------------------------------------------------------------------------------\n");
        
        sql.append("IF EXISTS (\n");
        sql.append("    SELECT 1 \n");
        sql.append("    FROM ESOCIAL_LOG_GER_FUNCIONARIO ELF WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF WITH(NOLOCK) ON GF.FUN_COD = ELF.FUN_COD\n");
        sql.append("    INNER JOIN ESOCIAL_ENVIO_DO_EVENTO EEE WITH(NOLOCK) ON EEE.ENV_COD = ELF.ENV_COD\n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append("\n");
        sql.append("      AND ELF.ESL_ORG_COD = ").append(orgCod).append("\n");
        sql.append("      AND EEE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND EEE.ENV_AMBIENTE = ").append(ambiente).append("\n");
        sql.append(")\n");
        sql.append("BEGIN\n");
        sql.append("    UPDATE ELF\n");
        sql.append("    SET ESL_NUMERO_RECIBO = '").append(recibo).append("',\n");
        sql.append("        ESL_ID_EVENTO = '").append(idEvento).append("'\n");
        sql.append("    FROM ESOCIAL_LOG_GER_FUNCIONARIO ELF WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_FUNCIONARIO GF WITH(NOLOCK) ON GF.FUN_COD = ELF.FUN_COD\n");
        sql.append("    INNER JOIN ESOCIAL_ENVIO_DO_EVENTO EEE WITH(NOLOCK) ON EEE.ENV_COD = ELF.ENV_COD\n");
        sql.append("    WHERE GF.FUN_MATRICULA = ").append(matricula).append("\n");
        sql.append("      AND ELF.ESL_ORG_COD = ").append(orgCod).append("\n");
        sql.append("      AND EEE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND EEE.ENV_AMBIENTE = ").append(ambiente).append("\n");
        sql.append("END\n");
        sql.append("ELSE\n");
        sql.append("BEGIN\n");
        // --- TRAVA DE SEGURANÇA ---
        sql.append("    IF EXISTS (SELECT 1 FROM GER_FUNCIONARIO WITH(NOLOCK) WHERE FUN_MATRICULA = ").append(matricula).append(")\n");
        sql.append("    BEGIN\n");
        sql.append("        INSERT INTO ESOCIAL_ENVIO_DO_EVENTO \n");
        sql.append("        (ETA_COD, USR_CODIGO, ENV_NOME_ARQUIVO, ENV_CAMINHO_ARQUIVO, ENV_PROTOCOLO, ENV_DATA_HORA, ENV_STATUS, ENV_MES_COD, ENV_ANO_COD, ENV_AMBIENTE, ORG_COD)\n");
        sql.append("        VALUES \n");
        sql.append("        ('").append(codEvento).append("', \n");
        sql.append("         ").append(usrCod).append(", \n");
        sql.append("         '").append(nomeArquivo).append("', \n");
        sql.append("         '").append(caminhoArquivo).append("', \n");
        sql.append("         '").append(protocolo).append("', \n");
        sql.append("         GETDATE(), 'P', ").append(mesProc).append(", ").append(anoProc).append(", ").append(ambiente).append(", ").append(orgCod).append(");\n\n");

        sql.append("        INSERT INTO ESOCIAL_LOG_GER_FUNCIONARIO \n");
        sql.append("        (FUN_COD, ENV_COD, RES_COD, ESL_NUMERO_RECIBO, ESL_ORG_COD, ESL_ID_EVENTO)\n");
        sql.append("        VALUES \n");
        sql.append("        ((SELECT TOP 1 FUN_COD FROM GER_FUNCIONARIO WITH(NOLOCK) WHERE FUN_MATRICULA = ").append(matricula).append("), \n");
        sql.append("         SCOPE_IDENTITY(), \n");
        sql.append("         7, \n");
        sql.append("         '").append(recibo).append("', \n");
        sql.append("         ").append(orgCod).append(", \n");
        sql.append("         '").append(idEvento).append("');\n");
        sql.append("    END\n");
        sql.append("END;\n\n");
        
        return sql.toString();
    }

    /**
     * GERAÇÃO FASE 3 (S-1200, S-1210, S-1202)
     * Chave: GER_PESSOA_FISICA (CPF) + Competência (Mês/Ano)
     */
    private String gerarScriptFase3(InfoReciboDTO info, String codEvento, int orgCod, int ambiente, int usrCod) {
        StringBuilder sql = new StringBuilder();

        String cpf = info.getCpf() != null ? info.getCpf() : "0";
        String recibo = info.getRecibo();
        String idEvento = info.getId();
        String protocolo = info.getProtocolo() != null ? info.getProtocolo() : "";
        String nomeArquivo = info.getNomeArquivo();
        String caminhoArquivo = info.getCaminhoArquivo() != null ? info.getCaminhoArquivo().replace("\\", "\\\\") : "";
        String perApur = info.getPerApur() != null ? info.getPerApur() : "";

        // --- LÓGICA DE COMPETÊNCIA ---
        String anoComp = "NULL";
        String mesComp = "NULL";

        if (perApur.length() >= 7) {
            // Padrão AAAA-MM
            anoComp = perApur.substring(0, 4);
            mesComp = perApur.substring(5, 7);
        } else if (perApur.length() == 4) {
            // Anual AAAA (13º)
            anoComp = perApur;
            mesComp = "13";
        }

        sql.append("/* \n");
        sql.append("   ARQUIVO: ").append(nomeArquivo).append("\n");
        sql.append("   EVENTO: ").append(codEvento).append(" (Fase 3)\n");
        sql.append("   CPF: ").append(cpf).append("\n");
        sql.append("   COMPETÊNCIA: ").append(perApur).append(" (Mês: ").append(mesComp).append(", Ano: ").append(anoComp).append(")\n");
        sql.append("*/\n\n");

        // BLOCO 1: CONTROLA ENVIO
        sql.append("--------------------------------------------------------------------------------\n");
        sql.append("-- BLOCO 1: ATUALIZAÇÃO DA TABELA DE CONTROLE\n");
        sql.append("--------------------------------------------------------------------------------\n");
        
        sql.append("IF EXISTS (\n");
        sql.append("    SELECT 1 FROM ESOCIAL_CONTROLA_ENVIO ECE WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_PESSOA_FISICA PF WITH(NOLOCK) ON PF.PES_COD = ECE.ECO_CHAVE \n");
        sql.append("    WHERE PF.PFI_CPF = '").append(cpf).append("'\n");
        sql.append("      AND ECE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND ECE.ECO_ANO = '").append(anoComp).append("'\n");
        sql.append("      AND ECE.ECO_MES = '").append(mesComp).append("'\n");
        sql.append("      AND ECE.ECO_AMBIENTE = ").append(ambiente).append("\n");
        sql.append(")\n");
        sql.append("BEGIN\n");
        sql.append("    UPDATE ESOCIAL_CONTROLA_ENVIO \n");
        sql.append("    SET ECO_RECIBO = '").append(recibo).append("',\n");
        sql.append("        ECO_SITUACAO = 'N'\n");
        sql.append("    FROM ESOCIAL_CONTROLA_ENVIO ECE WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_PESSOA_FISICA PF WITH(NOLOCK) ON PF.PES_COD = ECE.ECO_CHAVE\n");
        sql.append("    WHERE PF.PFI_CPF = '").append(cpf).append("'\n");
        sql.append("      AND ECE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND ECE.ECO_ANO = '").append(anoComp).append("'\n");
        sql.append("      AND ECE.ECO_MES = '").append(mesComp).append("'\n");
        sql.append("      AND ECE.ECO_AMBIENTE = ").append(ambiente).append("\n");
        sql.append("END\n");
        sql.append("ELSE\n");
        sql.append("BEGIN\n");
        // --- TRAVA DE SEGURANÇA ---
        sql.append("    IF EXISTS (SELECT 1 FROM GER_PESSOA_FISICA WITH(NOLOCK) WHERE PFI_CPF = '").append(cpf).append("')\n");
        sql.append("    BEGIN\n");
        sql.append("        INSERT INTO ESOCIAL_CONTROLA_ENVIO \n");
        sql.append("        (ETA_COD, ECO_RECIBO, ECO_CHAVE, ECO_NOME_CHAVE, ECO_TIPO, ECO_SITUACAO, ECO_ANO, ECO_MES, ECO_AMBIENTE, ORG_COD, ECO_CPF, ECO_NIS)\n");
        sql.append("        VALUES \n");
        sql.append("        ('").append(codEvento).append("', \n");
        sql.append("         '").append(recibo).append("', \n");
        sql.append("         (SELECT TOP 1 PES_COD FROM GER_PESSOA_FISICA WITH(NOLOCK) WHERE PFI_CPF = '").append(cpf).append("'), \n");
        sql.append("         'PES_COD', 'E', 'N', '").append(anoComp).append("', '").append(mesComp).append("', ").append(ambiente).append(", ").append(orgCod).append(", '").append(cpf).append("', '0')\n");
        sql.append("    END\n");
        sql.append("END;\n\n");

        // BLOCO 2: LOG E ENVIO
        sql.append("--------------------------------------------------------------------------------\n");
        sql.append("-- BLOCO 2: LOG E ENVIO (LOG DE PESSOA)\n");
        sql.append("--------------------------------------------------------------------------------\n");

        sql.append("IF EXISTS (\n");
        sql.append("    SELECT 1 \n");
        sql.append("    FROM ESOCIAL_LOG_GER_PESSOA ELP WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_PESSOA_FISICA PF WITH(NOLOCK) ON PF.PES_COD = ELP.PES_COD\n");
        sql.append("    INNER JOIN ESOCIAL_ENVIO_DO_EVENTO EEE WITH(NOLOCK) ON EEE.ENV_COD = ELP.ENV_COD\n");
        sql.append("    WHERE PF.PFI_CPF = '").append(cpf).append("'\n");
        sql.append("      AND ELP.ESL_ORG_COD = ").append(orgCod).append("\n");
        sql.append("      AND EEE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND EEE.ENV_ANO_COD = ").append(anoComp).append("\n");
        sql.append("      AND EEE.ENV_MES_COD = ").append(mesComp).append("\n");
        sql.append("      AND EEE.ENV_AMBIENTE = ").append(ambiente).append("\n");
        sql.append(")\n");
        sql.append("BEGIN\n");
        sql.append("    UPDATE ELP\n");
        sql.append("    SET ESL_NUMERO_RECIBO = '").append(recibo).append("',\n");
        sql.append("        ESL_ID_EVENTO = '").append(idEvento).append("'\n");
        sql.append("    FROM ESOCIAL_LOG_GER_PESSOA ELP WITH(NOLOCK)\n");
        sql.append("    INNER JOIN GER_PESSOA_FISICA PF WITH(NOLOCK) ON PF.PES_COD = ELP.PES_COD\n");
        sql.append("    INNER JOIN ESOCIAL_ENVIO_DO_EVENTO EEE WITH(NOLOCK) ON EEE.ENV_COD = ELP.ENV_COD\n");
        sql.append("    WHERE PF.PFI_CPF = '").append(cpf).append("'\n");
        sql.append("      AND ELP.ESL_ORG_COD = ").append(orgCod).append("\n");
        sql.append("      AND EEE.ETA_COD = '").append(codEvento).append("'\n");
        sql.append("      AND EEE.ENV_ANO_COD = ").append(anoComp).append("\n");
        sql.append("      AND EEE.ENV_MES_COD = ").append(mesComp).append("\n");
        sql.append("      AND EEE.ENV_AMBIENTE = ").append(ambiente).append("\n");
        sql.append("END\n");
        sql.append("ELSE\n");
        sql.append("BEGIN\n");
        // --- TRAVA DE SEGURANÇA ---
        sql.append("    IF EXISTS (SELECT 1 FROM GER_PESSOA_FISICA WITH(NOLOCK) WHERE PFI_CPF = '").append(cpf).append("')\n");
        sql.append("    BEGIN\n");
        sql.append("        INSERT INTO ESOCIAL_ENVIO_DO_EVENTO \n");
        sql.append("        (ETA_COD, USR_CODIGO, ENV_NOME_ARQUIVO, ENV_CAMINHO_ARQUIVO, ENV_PROTOCOLO, ENV_DATA_HORA, ENV_STATUS, ENV_MES_COD, ENV_ANO_COD, ENV_AMBIENTE, ORG_COD)\n");
        sql.append("        VALUES \n");
        sql.append("        ('").append(codEvento).append("', \n");
        sql.append("         ").append(usrCod).append(", \n");
        sql.append("         '").append(nomeArquivo).append("', \n");
        sql.append("         '").append(caminhoArquivo).append("', \n");
        sql.append("         '").append(protocolo).append("', \n");
        sql.append("         GETDATE(), 'P', ").append(mesComp).append(", ").append(anoComp).append(", ").append(ambiente).append(", ").append(orgCod).append(");\n\n");

        sql.append("        INSERT INTO ESOCIAL_LOG_GER_PESSOA \n");
        sql.append("        (PES_COD, ENV_COD, RES_COD, ESL_NUMERO_RECIBO, ESL_ORG_COD, ESL_ID_EVENTO)\n");
        sql.append("        VALUES \n");
        sql.append("        ((SELECT TOP 1 PES_COD FROM GER_PESSOA_FISICA WITH(NOLOCK) WHERE PFI_CPF = '").append(cpf).append("'), \n");
        sql.append("         SCOPE_IDENTITY(), \n");
        sql.append("         7, \n");
        sql.append("         '").append(recibo).append("', \n");
        sql.append("         ").append(orgCod).append(", \n");
        sql.append("         '").append(idEvento).append("');\n");
        sql.append("    END\n");
        sql.append("END;\n\n");

        return sql.toString();
    }
}