package com.recibos.recibos_service.util;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ArquivoXML {

    public InfoReciboDTO infXML(File arquivo, String tipoArquivoEve) throws ParserConfigurationException, SAXException {
        try {
            // 1. Determina a tag principal do evento
            String tipoEventoTag = getTipoEventoTag(tipoArquivoEve);

            // 2. Configura o SAX Parser
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // Desativa validação para evitar erros de XSD não encontrado ou conexões de rede
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
            
            SAXParser saxParser = factory.newSAXParser();
            
            // 3. Cria o Handler e passa o NOME e o CAMINHO do arquivo
            SaxHandler handler = new SaxHandler(tipoEventoTag, arquivo.getName(), arquivo.getAbsolutePath());

            // 4. Inicia o parsing
            saxParser.parse(arquivo, handler);

            return handler.getInfo();

        } catch (IOException e) {
            System.out.println("Erro IO: " + e.getMessage());
            throw new RuntimeException("Erro de IO ao fazer parse SAX do XML: " + arquivo.getName(), e);
        }
    }

    private String getTipoEventoTag(String tipoArquivoEve) {
        // Detecta o tipo baseando-se no nome do arquivo ou sufixo
        if (tipoArquivoEve.contains("2200")) return "evtAdmissao";
        if (tipoArquivoEve.contains("2299")) return "evtDeslig";
        if (tipoArquivoEve.contains("2300")) return "evtTSVInicio";
        if (tipoArquivoEve.contains("1200")) return "evtRemun";
        if (tipoArquivoEve.contains("1202")) return "evtRmnRPPS";
        if (tipoArquivoEve.contains("1210")) return "evtPgtos";
        // Adicione outros se necessário
        return "n";
    }

    /**
     * Handler SAX para ler o XML evento a evento.
     */
    private static class SaxHandler extends DefaultHandler {

        private InfoReciboDTO info = new InfoReciboDTO();
        private String tipoEventoTag;
        
        private StringBuilder currentText;
        
        // Flags de controle de onde estamos no XML
        private boolean inEvento = false;
        private boolean inRecibo = false;     // Dentro de <retornoEvento>
        private boolean inRecepcao = false;   // Dentro de <recepcao> (onde fica o protocolo)
        private boolean inProcessamento = false; // Dentro de <processamento> (onde fica a data)

        public SaxHandler(String tipoEventoTag, String nomeArquivo, String caminhoArquivo) {
            this.tipoEventoTag = tipoEventoTag;
            this.currentText = new StringBuilder();
            
            // Já preenche os dados do arquivo físico no DTO
            this.info.setNomeArquivo(nomeArquivo);
            this.info.setCaminhoArquivo(caminhoArquivo);
        }

        public InfoReciboDTO getInfo() {
            return info;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            currentText.setLength(0); // Limpa buffer

            // Verifica em qual bloco estamos entrando
            if (qName.equals(this.tipoEventoTag)) {
                inEvento = true;
                // Pega o ID do atributo da tag do evento (ex: <evtAdmissao Id="...">)
                String idVal = attributes.getValue("Id");
                if (idVal != null) info.setId(idVal);
            } 
            else if (qName.equals("retornoEvento")) {
                inRecibo = true;
            }
            else if (qName.equals("recepcao")) {
                inRecepcao = true;
            }
            else if (qName.equals("processamento")) {
                inProcessamento = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            // Acumula o texto (necessário para XMLs grandes ou caracteres especiais)
            currentText.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String valor = currentText.toString().trim();

            if (inEvento) {
                // Captura dados do Evento (CPF, Matrícula, Período)
                switch (qName) {
                    case "cpfTrab":
                    case "cpfBenef": // S-1210 usa cpfBenef
                        info.setCpf(valor);
                        break;
                    case "matricula":
                        info.setMatricula(valor);
                        break;
                    case "perApur":
                        info.setPerApur(valor);
                        break;
                }
                
                // Se fechou a tag do evento, sai do modo inEvento
                if (qName.equals(this.tipoEventoTag)) {
                    inEvento = false;
                }
            } 
            else if (inRecibo) {
                // Se fechou a tag de recibo
                if (qName.equals("retornoEvento")) {
                    inRecibo = false;
                }
                // Dentro do retornoEvento, temos sub-blocos:
                
                if (inRecepcao) {
                    if (qName.equals("protocoloEnvioLote")) {
                        info.setProtocolo(valor);
                    }
                    if (qName.equals("recepcao")) {
                        inRecepcao = false;
                    }
                }
                
                if (inProcessamento) {
                    if (qName.equals("dhProcessamento")) {
                        info.setDhProcessamento(valor);
                    }
                    if (qName.equals("processamento")) {
                        inProcessamento = false;
                    }
                }

                // Tag <nrRecibo> costuma ficar direta dentro de <recibo> que está dentro de <retornoEvento>
                // Mas às vezes a estrutura varia. O padrão comum é <recibo><nrRecibo>...</nrRecibo></recibo>
                if (qName.equals("nrRecibo")) {
                    info.setRecibo(valor);
                }
            }
        }
    }
}