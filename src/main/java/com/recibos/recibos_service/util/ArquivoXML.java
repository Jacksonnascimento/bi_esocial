package com.recibos.recibos_service.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ArquivoXML {

    /**
     * MÉTODOS DE ENTRADA
     */
    
    // Para arquivo físico (.xml solto na pasta)
    public InfoReciboDTO infXML(File arquivo, String tipoArquivoEve) throws ParserConfigurationException, SAXException {
        try (InputStream is = new FileInputStream(arquivo)) {
            return parse(is, tipoArquivoEve, arquivo.getName(), arquivo.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Erro de IO ao ler arquivo: " + arquivo.getName(), e);
        }
    }

    // Para arquivo dentro de ZIP (InputStream)
    // CORREÇÃO AQUI: Adicionado "IOException" no throws
    public InfoReciboDTO infXML(InputStream is, String nomeArquivo, String caminhoZip) throws ParserConfigurationException, SAXException, IOException {
        // Monta um "caminho virtual" para saber que veio de dentro do zip (Ex: pasta/arquivo.zip!arquivo.xml)
        String caminhoVirtual = caminhoZip + "!" + nomeArquivo;
        return parse(is, nomeArquivo, nomeArquivo, caminhoVirtual);
    }

    /**
     * LÓGICA CENTRAL DE PARSE (Privada e Reutilizável)
     */
    private InfoReciboDTO parse(InputStream is, String tipoEventoStr, String nomeArquivo, String caminhoCompleto) 
            throws ParserConfigurationException, SAXException, IOException {
        
        String tipoEventoTag = getTipoEventoTag(tipoEventoStr);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        
        SAXParser saxParser = factory.newSAXParser();
        
        // Passa o InputStream direto para o parser
        SaxHandler handler = new SaxHandler(tipoEventoTag, nomeArquivo, caminhoCompleto);
        saxParser.parse(is, handler);

        return handler.getInfo();
    }

    private String getTipoEventoTag(String tipoArquivoEve) {
        if (tipoArquivoEve.contains("2200")) return "evtAdmissao";
        if (tipoArquivoEve.contains("2299")) return "evtDeslig";
        if (tipoArquivoEve.contains("2300")) return "evtTSVInicio";
        if (tipoArquivoEve.contains("1200")) return "evtRemun";
        if (tipoArquivoEve.contains("1202")) return "evtRmnRPPS";
        if (tipoArquivoEve.contains("1210")) return "evtPgtos";
        return "n";
    }

    private static class SaxHandler extends DefaultHandler {
        private InfoReciboDTO info = new InfoReciboDTO();
        private String tipoEventoTag;
        private StringBuilder currentText;
        
        private boolean inEvento = false;
        private boolean inRecibo = false;
        private boolean inRecepcao = false;
        private boolean inProcessamento = false;

        public SaxHandler(String tipoEventoTag, String nomeArquivo, String caminhoArquivo) {
            this.tipoEventoTag = tipoEventoTag;
            this.currentText = new StringBuilder();
            this.info.setNomeArquivo(nomeArquivo);
            this.info.setCaminhoArquivo(caminhoArquivo);
        }

        public InfoReciboDTO getInfo() { return info; }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            currentText.setLength(0);
            if (qName.equals(this.tipoEventoTag)) {
                inEvento = true;
                String idVal = attributes.getValue("Id");
                if (idVal != null) info.setId(idVal);
            } else if (qName.equals("retornoEvento")) {
                inRecibo = true;
            } else if (qName.equals("recepcao")) {
                inRecepcao = true;
            } else if (qName.equals("processamento")) {
                inProcessamento = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            currentText.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String valor = currentText.toString().trim();

            if (inEvento) {
                switch (qName) {
                    case "cpfTrab": case "cpfBenef": info.setCpf(valor); break;
                    case "matricula": info.setMatricula(valor); break;
                    case "perApur": info.setPerApur(valor); break;
                }
                if (qName.equals(this.tipoEventoTag)) inEvento = false;
            } else if (inRecibo) {
                if (qName.equals("retornoEvento")) inRecibo = false;
                
                if (inRecepcao) {
                    if (qName.equals("protocoloEnvioLote")) info.setProtocolo(valor);
                    if (qName.equals("recepcao")) inRecepcao = false;
                }
                
                if (inProcessamento) {
                    if (qName.equals("dhProcessamento")) info.setDhProcessamento(valor);
                    if (qName.equals("processamento")) inProcessamento = false;
                }
                
                if (qName.equals("nrRecibo")) info.setRecibo(valor);
            }
        }
    }
}