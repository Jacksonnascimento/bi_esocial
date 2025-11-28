package com.recibos.recibos_service.util;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * MUDANÇA: Esta classe foi totalmente reescrita para usar um parser SAX.
 * O SAX (Simple API for XML) lê o arquivo em streaming (evento a evento),
 * em vez de carregar tudo na memória (DOM). Isso evita OutOfMemoryError
 * em arquivos grandes ou em lotes de milhares de arquivos.
 */
public class ArquivoXML {

    public InfoReciboDTO infXML(File arquivo, String tipoArquivoEve) throws ParserConfigurationException, SAXException {
        try {
            // 1. Determina a tag principal do evento (igual a antes)
            String tipoEventoTag = getTipoEventoTag(tipoArquivoEve);

            // 2. Configura o SAX Parser
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            
            // 3. Cria o "Handler" (Ouvinte) que irá ler o XML
            SaxHandler handler = new SaxHandler(tipoEventoTag);

            // 4. Inicia o parsing. O SAX irá "empurrar" eventos para o handler.
            saxParser.parse(arquivo, handler);

            // 5. Pega o DTO preenchido pelo handler
            return handler.getInfo();

        } catch (IOException e) {
            System.out.println(e);
            throw new RuntimeException("Erro de IO ao fazer parse SAX do XML", e);
        }
    }

    // Método de ajuda para mapear o nome do arquivo para a tag do evento
    private String getTipoEventoTag(String tipoArquivoEve) {
        switch(tipoArquivoEve){
            case "2200.xml": return "evtAdmissao";
            case "2299.xml": return "evtDeslig";
            case "2300.xml": return "evtTSSVInicio"; // ADICIONADO (Assumindo tag padrão)
            case "1200.xml": return "evtRemun";
            case "1202.xml": return "evtRmnRPPS";
            case "1210.xml": return "evtPgtos";
            case "3000.xml": return "evtExclusao";
            default: return "n";
        }
    }

    /**
     * Esta classe interna é o núcleo do parser SAX.
     * Ela ouve os eventos de "abrir tag", "fechar tag" e "ler caracteres".
     */
    private static class SaxHandler extends DefaultHandler {

        private InfoReciboDTO info = new InfoReciboDTO();
        private String tipoEventoTag;
        
        private StringBuilder currentText; // Armazena o texto dentro de uma tag
        private boolean inEvento = false;  // Flag para saber se estamos dentro da tag do evento
        private boolean inRecibo = false;  // Flag para saber se estamos dentro da tag de recibo

        public SaxHandler(String tipoEventoTag) {
            this.tipoEventoTag = tipoEventoTag;
            this.currentText = new StringBuilder();
        }

        public InfoReciboDTO getInfo() {
            return info;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            currentText.setLength(0); // Limpa o buffer de texto

            if (qName.equals(this.tipoEventoTag)) {
                inEvento = true;
                info.setId(attributes.getValue("Id")); // Pega o atributo "Id"
            } 
            else if (qName.equals("retornoEvento")) {
                inRecibo = true;
                // (Poderia pegar o Id do retornoEvento se precisasse)
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (inEvento || inRecibo) {
                currentText.append(ch, start, length); // Constrói o texto
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (inEvento) {
                // Estamos dentro da tag principal do evento
                switch (qName) {
                    case "cpfTrab":
                        info.setCpf(currentText.toString().trim());
                        break;
                    case "matricula":
                        info.setMatricula(currentText.toString().trim());
                        break;
                    case "cpfBenef": // Para o S-1210
                        info.setCpf(currentText.toString().trim());
                        break;
                    case "perApur":
                        info.setPerApur(currentText.toString().trim());
                        break;
                    case "nrRecEvt": // Para o S-3000
                        info.setNrRecEvt(currentText.toString().trim());
                        break;
                }

                if (qName.equals(this.tipoEventoTag)) {
                    inEvento = false; // Saímos da tag do evento
                }
            } 
            else if (inRecibo) {
                // Estamos dentro da tag de recibo
                if (qName.equals("nrRecibo")) {
                    info.setRecibo(currentText.toString().trim());
                }
                
                if (qName.equals("retornoEvento")) {
                    inRecibo = false; // Saímos da tag de recibo
                }
            }
        }
    }
}