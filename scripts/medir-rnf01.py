"""Executa a sondagem RNF-01 contra um servidor HTTPS local e um banco descartável.

Consulte docs/evidencias/rnf-01-mysql.md para os pré-requisitos e o protocolo.
"""

import concurrent.futures
import getpass
import http.client
import json
import math
import re
import ssl
import statistics
import sys
import time
import urllib.parse
import uuid
from threading import Barrier


BASE = "/api/v1/requisicoes"
RECURSO = "10000000-0000-4000-8000-000000000002"
AMOSTRAS = 32


def chamada(conexao, cookies, metodo, caminho, corpo=None, cabecalhos=None):
    headers = dict(cabecalhos or {})
    if cookies:
        headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in cookies.items())
    conexao.request(metodo, caminho, body=corpo, headers=headers)
    resposta = conexao.getresponse()
    for nome, valor in resposta.getheaders():
        if nome.lower() == "set-cookie":
            chave, conteudo = valor.split(";", 1)[0].split("=", 1)
            cookies[chave] = conteudo
    return resposta.status, resposta.read()


def autenticar(conexao, senha):
    cookies = {}
    status, pagina = chamada(conexao, cookies, "GET", "/login")
    assert status == 200
    token = re.search(rb'name="_csrf"[^>]*value="([^"]+)"', pagina).group(1).decode()
    form = urllib.parse.urlencode({"username": "desenvolvedor", "password": senha, "_csrf": token})
    status, _ = chamada(conexao, cookies, "POST", "/login", form,
                        {"Content-Type": "application/x-www-form-urlencoded"})
    assert status == 302
    status, resposta = chamada(conexao, cookies, "GET", "/api/v1/csrf")
    assert status == 200
    token = json.loads(resposta)
    return cookies, {token["cabecalho"]: token["token"]}


def trabalhador(porta, tls, senha, barreira, quantidade, esperado, amostras):
    conexao = http.client.HTTPSConnection("127.0.0.1", porta, context=tls, timeout=10)
    try:
        cookies, csrf = autenticar(conexao, senha)
        pedidos = [str(uuid.uuid4()) for _ in range(amostras + 1)]
        for pedido in pedidos:
            corpo = json.dumps({"requisicaoId": pedido, "itens": [
                {"recursoId": RECURSO, "quantidade": quantidade}
            ]})
            status, resposta = chamada(conexao, cookies, "POST", BASE, corpo,
                                       {"Content-Type": "application/json", **csrf})
            assert status == 201, (status, resposta)
            assert json.loads(resposta)["requisicao"]["estado"] == "INICIAL"

        def validar(pedido):
            socket_anterior = conexao.sock
            inicio = time.perf_counter_ns()
            status, resposta = chamada(conexao, cookies, "POST", f"{BASE}/{pedido}/validacao", b"", csrf)
            milissegundos = (time.perf_counter_ns() - inicio) / 1e6
            assert socket_anterior is not None and conexao.sock is socket_anterior, "Conexão TLS recriada"
            assert status == 200, (status, resposta)
            assert json.loads(resposta)["resultado"] == esperado, resposta
            return milissegundos

        validar(pedidos[0])
        duracoes = []
        for pedido in pedidos[1:]:
            barreira.wait(timeout=30)
            duracoes.append(validar(pedido))
        return duracoes
    finally:
        conexao.close()


def resumo(nome, duracoes):
    ordenadas = sorted(duracoes)
    p95 = ordenadas[math.ceil(len(ordenadas) * .95) - 1]
    acima = sum(ms >= 2000 for ms in duracoes)
    print(f"{nome};{len(duracoes)};{statistics.median(duracoes):.2f};"
          f"{p95:.2f};{max(duracoes):.2f};{acima}", flush=True)


def medir_conexao(porta, tls):
    # Cronometra connect() e handshake separadamente, sem enviar HTTP.
    import socket

    for host in ("localhost", "127.0.0.1"):
        tcp, handshake = [], []
        for _ in range(5):
            inicio = time.perf_counter_ns()
            with socket.create_connection((host, porta), timeout=8) as bruto:
                pronto = time.perf_counter_ns()
                with tls.wrap_socket(bruto, server_hostname=host):
                    seguro = time.perf_counter_ns()
            tcp.append((pronto - inicio) / 1e6)
            handshake.append((seguro - pronto) / 1e6)
        resumo(f"{host} TCP", tcp)
        resumo(f"{host} TLS", handshake)


def main():
    porta, certificado = int(sys.argv[1]), sys.argv[2]
    tls = ssl.create_default_context(cafile=certificado)
    senha = getpass.getpass("Senha da conta desenvolvedor na base descartável: ")
    print("cenário;amostras;mediana_ms;p95_ms;max_ms;duracoes_ge_2s")
    medir_conexao(porta, tls)
    for quantidade, esperado in [(720, "REVISAO_POR_RESERVA"),
                                 (801, "REVISAO_POR_SALDO_INSUFICIENTE"), (1, "LIBERADA")]:
        for simultaneas in (1, 8):
            barreira = Barrier(simultaneas)
            with concurrent.futures.ThreadPoolExecutor(max_workers=simultaneas) as pool:
                tarefas = [pool.submit(trabalhador, porta, tls, senha, barreira, quantidade,
                                       esperado, AMOSTRAS // simultaneas)
                           for _ in range(simultaneas)]
                resumo(f"{esperado} concorrência={simultaneas}",
                       [ms for tarefa in tarefas for ms in tarefa.result()])


if __name__ == "__main__":
    main()
