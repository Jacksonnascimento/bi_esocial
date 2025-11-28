import os
import shutil
import xml.etree.ElementTree as ET
import multiprocessing
import functools
import time
import zipfile  # Necessário para ler arquivos .zip

# --- CONFIGURAÇÃO ---
PASTA_ORIGEM = r'C:\Users\jacks\Downloads\JUAZEIRO\JUAZEIRO'
PASTA_DESTINO = r'C:\Users\jacks\Downloads\Destino'

# Valor do Recibo
VALOR_PROCURADO = '1.1.0000000035622958733'

# Tag XML alvo
TAG_ALVO = 'nrRecibo'
# --- FIM DA CONFIGURAÇÃO ---

def verificar_conteudo_xml(tree, tag_alvo, valor_procurado):
    """
    Função auxiliar que recebe uma árvore XML (ET tree) e verifica se a tag bate.
    Retorna True se encontrou, False caso contrário.
    """
    root = tree.getroot()
    for element in root.iter():
        nome_local_da_tag = element.tag.split('}')[-1]
        if nome_local_da_tag == tag_alvo:
            if element.text is not None and element.text.strip() == valor_procurado:
                return True
    return False

def processar_arquivo(caminho_arquivo_origem, valor_procurado, pasta_destino, tag_alvo):
    """
    Processa arquivos .xml (move) ou .zip (extrai o xml correspondente).
    """
    nome_arquivo = os.path.basename(caminho_arquivo_origem)
    extensao = os.path.splitext(nome_arquivo)[1].lower()

    try:
        # ---------------------------------------------------------
        # CASO 1: É UM ARQUIVO XML SOLTO
        # ---------------------------------------------------------
        if extensao == '.xml':
            if not os.path.exists(caminho_arquivo_origem):
                return (nome_arquivo, 'ignorado', 'Arquivo não existe mais')

            tree = ET.parse(caminho_arquivo_origem)
            encontrou = verificar_conteudo_xml(tree, tag_alvo, valor_procurado)

            if encontrou:
                caminho_arquivo_destino = os.path.join(pasta_destino, nome_arquivo)
                if os.path.exists(caminho_arquivo_destino):
                    return (nome_arquivo, 'erro', 'Arquivo já existe no destino')
                
                shutil.move(caminho_arquivo_origem, caminho_arquivo_destino)
                return (nome_arquivo, 'movido')

            return (nome_arquivo, 'ignorado')

        # ---------------------------------------------------------
        # CASO 2: É UM ARQUIVO ZIP
        # ---------------------------------------------------------
        elif extensao == '.zip':
            arquivos_encontrados_no_zip = 0
            
            # Abre o ZIP em modo leitura
            with zipfile.ZipFile(caminho_arquivo_origem, 'r') as z:
                # Lista todos os arquivos dentro do ZIP
                for file_info in z.infolist():
                    if file_info.filename.lower().endswith('.xml'):
                        # Abre o arquivo XML que está DENTRO do zip sem extrair para o disco
                        with z.open(file_info) as xml_file:
                            try:
                                tree = ET.parse(xml_file)
                                encontrou = verificar_conteudo_xml(tree, tag_alvo, valor_procurado)
                                
                                if encontrou:
                                    # Se achou, extrai esse arquivo específico
                                    nome_xml_interno = os.path.basename(file_info.filename)
                                    caminho_destino_final = os.path.join(pasta_destino, nome_xml_interno)
                                    
                                    # Previne sobrescrita se tiver nomes iguais
                                    if os.path.exists(caminho_destino_final):
                                        # Adiciona o nome do zip como prefixo para evitar erro
                                        nome_novo = f"{os.path.splitext(nome_arquivo)[0]}_{nome_xml_interno}"
                                        caminho_destino_final = os.path.join(pasta_destino, nome_novo)

                                    # Extrai direto do zip para o destino
                                    with open(caminho_destino_final, 'wb') as f_out:
                                        with z.open(file_info) as f_in:
                                            shutil.copyfileobj(f_in, f_out)
                                    
                                    arquivos_encontrados_no_zip += 1
                            
                            except ET.ParseError:
                                # Se um xml dentro do zip estiver corrompido, apenas ignora ele
                                continue

            if arquivos_encontrados_no_zip > 0:
                return (nome_arquivo, 'extraido_zip', f'{arquivos_encontrados_no_zip} arquivo(s) encontrado(s)')
            else:
                return (nome_arquivo, 'ignorado')

        return (nome_arquivo, 'ignorado')

    except ET.ParseError as e:
        return (nome_arquivo, 'erro', f'XML mal formatado: {e}')
    except zipfile.BadZipFile:
        return (nome_arquivo, 'erro', 'Arquivo ZIP corrompido')
    except Exception as e:
        return (nome_arquivo, 'erro', f'Exceção: {e}')

def main():
    print(f"Iniciando verificação em XMLs e ZIPs em: {PASTA_ORIGEM}")
    print(f"Procurando tag <{TAG_ALVO}> = {VALOR_PROCURADO}\n")
    
    start_time = time.time()
    
    if not os.path.exists(PASTA_DESTINO):
        os.makedirs(PASTA_DESTINO)

    print("Listando arquivos (.xml e .zip)...")
    arquivos_para_processar = []
    try:
        for dirpath, dirnames, filenames in os.walk(PASTA_ORIGEM):
            for filename in filenames:
                # --- MUDANÇA: Aceita .xml e .zip ---
                if filename.lower().endswith(('.xml', '.zip')):
                    caminho_completo = os.path.join(dirpath, filename)
                    arquivos_para_processar.append(caminho_completo)
                    
    except Exception as e:
        print(f"Erro ao listar arquivos: {e}")
        return

    total_de_arquivos = len(arquivos_para_processar)
    if total_de_arquivos == 0:
        print("Nenhum arquivo .xml ou .zip encontrado.")
        return
        
    print(f"Total de {total_de_arquivos} arquivos (XML/ZIP) encontrados. Processando...\n")

    worker_func = functools.partial(processar_arquivo, 
                                    valor_procurado=VALOR_PROCURADO, 
                                    pasta_destino=PASTA_DESTINO,
                                    tag_alvo=TAG_ALVO)

    num_processos = multiprocessing.cpu_count()
    
    contadores = {
        'movidos': 0,        # XMLs soltos movidos
        'extraidos': 0,      # XMLs tirados de dentro de ZIPs
        'ignorados': 0,
        'erros': 0
    }
    
    with multiprocessing.Pool(processes=num_processos) as pool:
        resultados = pool.imap_unordered(worker_func, arquivos_para_processar)
        
        for i, resultado in enumerate(resultados):
            nome_arquivo, status, *detalhe = resultado
            
            if status == 'movido':
                contadores['movidos'] += 1
            elif status == 'extraido_zip':
                # detalhe[0] contém mensagem de quantos arquivos foram extraídos deste zip
                contadores['extraidos'] += 1
                print(f"[ZIP] Encontrado em {nome_arquivo}: {detalhe[0]}")
            elif status == 'ignorado':
                contadores['ignorados'] += 1
            elif status == 'erro':
                contadores['erros'] += 1
                print(f"[ERRO] {nome_arquivo}: {detalhe[0]}")
            
            if (i + 1) % 1000 == 0:
                print(f"--- Progresso: {i + 1} / {total_de_arquivos} ---")

    end_time = time.time()
    duracao = end_time - start_time
    
    print("\n--- Concluído ---")
    print(f"Tempo: {duracao:.2f}s")
    print(f"XMLs soltos movidos: {contadores['movidos']}")
    print(f"ZIPs contendo o arquivo (extraídos): {contadores['extraidos']}")
    print(f"Erros: {contadores['erros']}")

if __name__ == '__main__':
    multiprocessing.freeze_support() 
    main()