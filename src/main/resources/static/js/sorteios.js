/* ==========================================================================
   SEPLAG Eventos — Sorteios
   Validações de interface, confirmação, revelação com suspense, modo
   apresentação, busca, cópia de e-mail e filtros do histórico.
   O sorteio em si é sempre executado no servidor.
   ========================================================================== */

(function () {
    'use strict';

    var movimentoReduzido = window.matchMedia
        ? window.matchMedia('(prefers-reduced-motion: reduce)')
        : null;

    function prefereMenosMovimento() {
        return !!(movimentoReduzido && movimentoReduzido.matches);
    }

    function porId(id) {
        return document.getElementById(id);
    }

    function todos(seletor, raiz) {
        return Array.prototype.slice.call((raiz || document).querySelectorAll(seletor));
    }

    function mostrar(elemento) {
        if (elemento) {
            elemento.classList.remove('hidden');
        }
    }

    function esconder(elemento) {
        if (elemento) {
            elemento.classList.add('hidden');
        }
    }

    function travarPagina(travar) {
        document.body.classList.toggle('sorteios-travado', !!travar);
    }

    function normalizar(valor) {
        return (valor || '')
            .toString()
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '');
    }

    function rotuloElegiveis(total) {
        return total + (total === 1 ? ' participante elegível' : ' participantes elegíveis');
    }

    function mostrarToast(mensagem) {
        var regiao = porId('toast-region');
        if (!regiao) {
            return;
        }
        var toast = document.createElement('div');
        toast.className = 'sorteios-toast';
        toast.setAttribute('role', 'status');
        toast.textContent = mensagem;
        regiao.appendChild(toast);
        window.setTimeout(function () {
            toast.classList.add('is-hiding');
            window.setTimeout(function () {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
            }, 280);
        }, 4600);
    }

    function agendarToastsIniciais() {
        todos('.sorteios-toast').forEach(function (toast) {
            window.setTimeout(function () {
                toast.classList.add('is-hiding');
                window.setTimeout(function () {
                    if (toast.parentNode) {
                        toast.parentNode.removeChild(toast);
                    }
                }, 280);
            }, 5200);
        });
    }

    function fecharDialogo(dialogo) {
        if (dialogo && typeof dialogo.close === 'function') {
            dialogo.close();
        }
    }

    // ---------------------------------------------------------------------
    // Percentual mínimo
    // ---------------------------------------------------------------------
    (function configurarPercentual() {
        var formulario = porId('form-percentual');
        var campo = porId('percentualMinimo');
        var botao = porId('btn-salvar-percentual');
        if (!formulario || !campo || !botao) {
            return;
        }

        var valorInicial = campo.value.trim() === '' ? null : Number(campo.value);

        function valorAtual() {
            if (campo.value.trim() === '') {
                return null;
            }
            var numero = Number(campo.value);
            return Number.isFinite(numero) ? numero : null;
        }

        function valido(numero) {
            return numero !== null && numero >= 0 && numero <= 100 && Math.floor(numero) === numero;
        }

        function atualizar() {
            var numero = valorAtual();
            var campoValido = valido(numero);
            var alterado = campoValido && numero !== valorInicial;

            campo.setAttribute('aria-invalid', campoValido ? 'false' : 'true');
            campo.classList.toggle('sorteios-input-invalido', !campoValido);
            botao.disabled = !campoValido || !alterado;
            botao.setAttribute('aria-disabled', botao.disabled ? 'true' : 'false');
        }

        campo.addEventListener('input', atualizar);
        campo.addEventListener('change', atualizar);
        atualizar();

        formulario.addEventListener('submit', function (evento) {
            var numero = valorAtual();
            if (!valido(numero) || numero === valorInicial) {
                evento.preventDefault();
                atualizar();
                campo.focus();
                return;
            }
            botao.disabled = true;
            botao.setAttribute('aria-busy', 'true');
            botao.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin" aria-hidden="true"></i> Salvando…';
        });
    })();

    // ---------------------------------------------------------------------
    // Quantidade, opção de não repetir e confirmação do sorteio
    // ---------------------------------------------------------------------
    var contextoFormularioSorteio = null;

    (function configurarFormularioSorteio() {
        var formulario = porId('form-sorteio');
        var campo = porId('quantidade');
        var alternarNaoRepetir = porId('naoRepetirVencedores');
        var botao = porId('btn-realizar-sorteio');
        var feedback = porId('aviso-quantidade');
        var feedbackNaoRepetir = porId('aviso-nao-repetir');
        if (!formulario || !campo || !botao || !feedback) {
            return;
        }

        var totalElegiveis = parseInt(formulario.getAttribute('data-total-elegiveis') || '0', 10);
        if (!Number.isFinite(totalElegiveis)) {
            totalElegiveis = 0;
        }
        var totalIneditos = parseInt(formulario.getAttribute('data-total-ineditos') || '0', 10);
        if (!Number.isFinite(totalIneditos)) {
            totalIneditos = totalElegiveis;
        }
        var percentual = parseInt(formulario.getAttribute('data-percentual') || '75', 10);
        if (!Number.isFinite(percentual)) {
            percentual = 75;
        }

        var linhasElegiveis = todos('#lista-elegiveis li');
        var contadorLista = porId('elegiveis-contador-lista');
        var contadorChip = porId('elegiveis-contador-chip');

        function naoRepetir() {
            return !!(alternarNaoRepetir && alternarNaoRepetir.checked);
        }

        function disponiveis() {
            return naoRepetir() ? totalIneditos : totalElegiveis;
        }

        function quantidadeAtual() {
            if (campo.value.trim() === '') {
                return null;
            }
            var numero = Number(campo.value);
            return Number.isFinite(numero) ? numero : null;
        }

        function atualizarListaElegiveis() {
            var total = disponiveis();
            if (contadorLista) {
                contadorLista.textContent = String(total);
            }
            if (contadorChip) {
                contadorChip.textContent = String(total);
            }
            linhasElegiveis.forEach(function (linha) {
                var jaVenceu = linha.getAttribute('data-vencedor') === 'true';
                linha.classList.toggle('is-fora-da-rodada', naoRepetir() && jaVenceu);
            });
            campo.setAttribute('max', String(Math.max(1, total)));
        }

        function validar() {
            var quantidade = quantidadeAtual();
            var total = disponiveis();
            var mensagem = '';
            var valido = true;

            if (totalElegiveis === 0) {
                mensagem = 'Ainda não há participantes elegíveis. Registre presenças e confira o percentual mínimo.';
                valido = false;
            } else if (total === 0) {
                mensagem = 'Todos os elegíveis já venceram. Desative a opção de não repetir vencedores para sortear novamente.';
                valido = false;
            } else if (quantidade === null) {
                mensagem = 'Campo obrigatório: informe quantas pessoas serão sorteadas.';
                valido = false;
            } else if (quantidade < 1 || Math.floor(quantidade) !== quantidade) {
                mensagem = 'Informe pelo menos 1 pessoa (número inteiro).';
                valido = false;
            } else if (quantidade > total) {
                mensagem = 'Há apenas ' + rotuloElegiveis(total) + ' disponível(is) nesta configuração.';
                valido = false;
            }

            campo.setAttribute('aria-invalid', valido ? 'false' : 'true');
            campo.classList.toggle('sorteios-input-invalido', !valido);
            feedback.textContent = mensagem;
            botao.disabled = !valido;
            botao.setAttribute('aria-disabled', botao.disabled ? 'true' : 'false');

            if (feedbackNaoRepetir) {
                if (naoRepetir()) {
                    feedbackNaoRepetir.textContent = total + ' de ' + totalElegiveis
                        + ' elegíveis disponíveis nesta rodada (vencedores excluídos).';
                } else {
                    feedbackNaoRepetir.textContent = '';
                }
            }

            return { valido: valido, quantidade: quantidade, mensagem: mensagem, total: total };
        }

        campo.addEventListener('input', validar);
        campo.addEventListener('change', validar);
        if (alternarNaoRepetir) {
            alternarNaoRepetir.addEventListener('change', function () {
                atualizarListaElegiveis();
                validar();
            });
        }
        atualizarListaElegiveis();
        validar();

        contextoFormularioSorteio = {
            totalElegiveis: totalElegiveis,
            percentual: percentual,
            campoQuantidade: campo,
            validar: validar
        };

        var dialogo = porId('dialog-sorteio');
        var resumo = porId('dialog-sorteio-resumo');
        var confirmar = porId('confirmar-sorteio');
        var confirmado = false;

        formulario.addEventListener('submit', function (evento) {
            if (confirmado) {
                return;
            }
            var resultado = validar();
            if (!resultado.valido) {
                evento.preventDefault();
                campo.focus();
                return;
            }
            evento.preventDefault();

            var quantidade = resultado.quantidade;
            var texto = 'Sortear ' + quantidade + (quantidade === 1 ? ' pessoa' : ' pessoas')
                + ' entre ' + resultado.total + ' disponíveis (frequência ≥ ' + percentual + '%). '
                + (naoRepetir() ? 'Sem repetir vencedores.' : 'Repetição de vencedores permitida.');

            if (resumo) {
                resumo.textContent = texto;
            }
            if (dialogo && typeof dialogo.showModal === 'function') {
                dialogo.showModal();
            } else if (window.confirm(texto)) {
                confirmado = true;
                formulario.submit();
            }
        });

        if (confirmar) {
            confirmar.addEventListener('click', function () {
                confirmado = true;
                fecharDialogo(dialogo);
                formulario.submit();
            });
        }

        if (dialogo) {
            dialogo.addEventListener('close', function () {
                confirmado = false;
            });
            dialogo.addEventListener('click', function (evento) {
                if (evento.target === dialogo) {
                    fecharDialogo(dialogo);
                }
            });
        }
    })();

    // ---------------------------------------------------------------------
    // Busca na lista de elegíveis
    // ---------------------------------------------------------------------
    (function configurarBuscaElegiveis() {
        var campo = porId('buscaElegiveis');
        var lista = porId('lista-elegiveis');
        var vazio = porId('busca-elegiveis-vazia');
        if (!campo || !lista) {
            return;
        }

        var itens = todos('li', lista);
        campo.addEventListener('input', function () {
            var termo = normalizar(campo.value.trim());
            var visiveis = 0;

            itens.forEach(function (item) {
                var conteudo = normalizar(item.getAttribute('data-nome') + ' ' + item.getAttribute('data-email'));
                var combina = termo === '' || conteudo.indexOf(termo) !== -1;
                item.classList.toggle('hidden', !combina);
                if (combina) {
                    visiveis++;
                }
            });

            if (vazio) {
                vazio.classList.toggle('hidden', visiveis !== 0);
            }
        });
    })();

    // ---------------------------------------------------------------------
    // Rolagem suave até o histórico
    // ---------------------------------------------------------------------
    (function configurarRolagemHistorico() {
        todos('[data-scroll-historico]').forEach(function (link) {
            link.addEventListener('click', function (evento) {
                var destino = porId('historico-sorteios');
                if (!destino) {
                    return;
                }
                evento.preventDefault();
                destino.scrollIntoView({
                    behavior: prefereMenosMovimento() ? 'auto' : 'smooth',
                    block: 'start'
                });
                var titulo = porId('historico-sorteios-titulo');
                if (titulo) {
                    window.setTimeout(function () {
                        titulo.focus({ preventScroll: true });
                    }, prefereMenosMovimento() ? 0 : 420);
                }
            });
        });
    })();

    // ---------------------------------------------------------------------
    // Copiar e enviar e-mail
    // ---------------------------------------------------------------------
    function copiarTexto(texto) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(texto);
        }
        return new Promise(function (resolver, rejeitar) {
            var area = document.createElement('textarea');
            area.value = texto;
            area.setAttribute('readonly', 'readonly');
            area.style.position = 'fixed';
            area.style.opacity = '0';
            document.body.appendChild(area);
            area.select();
            try {
                if (document.execCommand('copy')) {
                    resolver();
                } else {
                    rejeitar(new Error('Não foi possível copiar.'));
                }
            } catch (erro) {
                rejeitar(erro);
            } finally {
                document.body.removeChild(area);
            }
        });
    }

    (function configurarEmails() {
        document.addEventListener('click', function (evento) {
            var copiar = evento.target.closest('[data-copiar-email]');
            if (copiar) {
                var emailCopiar = copiar.getAttribute('data-email');
                if (!emailCopiar) {
                    return;
                }
                copiarTexto(emailCopiar).then(function () {
                    mostrarToast('Copiado! ' + emailCopiar);
                    var tituloAnterior = copiar.getAttribute('title');
                    copiar.setAttribute('title', 'Copiado!');
                    window.setTimeout(function () {
                        copiar.setAttribute('title', tituloAnterior || 'Copiar e-mail');
                    }, 1800);
                }).catch(function () {
                    mostrarToast('Não foi possível copiar o e-mail. Selecione o endereço manualmente.');
                });
                return;
            }

            var enviar = evento.target.closest('[data-enviar-email]');
            if (enviar) {
                var email = enviar.getAttribute('data-email');
                if (!email) {
                    return;
                }
                var nome = enviar.getAttribute('data-nome') || '';
                var eventoNome = enviar.getAttribute('data-evento') || 'nosso evento';
                var assunto = 'Parabéns! Você foi sorteado(a) — ' + eventoNome;
                var corpo = 'Olá, ' + nome + '!\n\n'
                    + 'Parabéns! Você foi sorteado(a) no evento ' + eventoNome + '.\n\n'
                    + 'Entre em contato com a organização para mais informações.\n\n'
                    + 'Equipe ' + eventoNome;
                window.location.href = 'mailto:' + encodeURIComponent(email)
                    + '?subject=' + encodeURIComponent(assunto)
                    + '&body=' + encodeURIComponent(corpo);
            }
        });
    })();

    // ---------------------------------------------------------------------
    // Confirmação genérica (desfazer rodada)
    // ---------------------------------------------------------------------
    (function configurarConfirmacaoGenerica() {
        var dialogo = porId('dialog-confirmacao');
        var texto = porId('dialog-confirmacao-texto');
        var confirmar = porId('confirmar-acao');
        var formularioPendente = null;

        document.addEventListener('submit', function (evento) {
            var formulario = evento.target.closest('form[data-confirmar]');
            if (!formulario || formulario.getAttribute('data-confirmado') === 'true') {
                return;
            }
            evento.preventDefault();
            formularioPendente = formulario;
            var mensagem = formulario.getAttribute('data-confirmar') || 'Tem certeza que deseja continuar?';
            if (texto) {
                texto.textContent = mensagem;
            }
            if (dialogo && typeof dialogo.showModal === 'function') {
                dialogo.showModal();
            } else if (window.confirm(mensagem)) {
                formulario.setAttribute('data-confirmado', 'true');
                formulario.submit();
            }
        });

        if (confirmar) {
            confirmar.addEventListener('click', function () {
                if (!formularioPendente) {
                    return;
                }
                var formulario = formularioPendente;
                formularioPendente = null;
                fecharDialogo(dialogo);
                formulario.setAttribute('data-confirmado', 'true');
                formulario.submit();
            });
        }

        if (dialogo) {
            dialogo.addEventListener('close', function () {
                formularioPendente = null;
            });
            dialogo.addEventListener('click', function (evento) {
                if (evento.target === dialogo) {
                    fecharDialogo(dialogo);
                }
            });
        }
    })();

    // Fecha qualquer diálogo pelos botões de cancelar.
    document.addEventListener('click', function (evento) {
        var fechar = evento.target.closest('[data-fechar-dialogo]');
        if (fechar) {
            fecharDialogo(fechar.closest('dialog'));
        }
    });

    // ---------------------------------------------------------------------
    // Suspense e modo apresentação
    // ---------------------------------------------------------------------
    var suspense = porId('sorteio-suspense');
    var suspenseTitulo = porId('suspense-titulo');
    var suspenseContagem = porId('suspense-contagem');
    var suspenseRoleta = porId('suspense-roleta');
    var suspenseResultado = porId('suspense-resultado');
    var suspenseVencedores = porId('suspense-vencedores');
    var suspenseAcoes = porId('suspense-acoes');
    var suspenseStatus = porId('suspense-status');
    var apresentacao = porId('apresentacao');
    var apresentacaoRodada = porId('apresentacao-rodada');
    var apresentacaoContador = porId('apresentacao-contador');
    var apresentacaoNome = porId('apresentacao-nome');
    var apresentacaoAnterior = porId('apresentacao-anterior');
    var apresentacaoProximo = porId('apresentacao-proximo');
    var canvasConfete = porId('confete');
    var temporizadorSuspense = null;
    var quadroSuspense = null;
    var dadosApresentacao = null;
    var indiceApresentacao = 0;
    var quadroConfete = null;
    var entrouEmTelaCheia = false;

    function nomesElegiveis() {
        return todos('#lista-elegiveis li')
            .map(function (item) { return item.getAttribute('data-nome'); })
            .filter(function (nome) { return nome && nome.trim() !== ''; });
    }

    function lerDadosDestaque() {
        var fonte = porId('resultado-destaque');
        if (!fonte) {
            return null;
        }
        var vencedores = todos('[data-nome]', fonte)
            .map(function (item) { return item.getAttribute('data-nome'); })
            .filter(function (nome) { return nome && nome.trim() !== ''; });
        if (vencedores.length === 0) {
            return null;
        }
        return {
            rodada: fonte.getAttribute('data-rodada') || '',
            vencedores: vencedores
        };
    }

    function lerDadosApresentacao() {
        var destaque = lerDadosDestaque();
        if (destaque) {
            return destaque;
        }
        var fonte = porId('apresentacao-dados');
        if (!fonte) {
            return null;
        }
        var vencedores = todos('[data-vencedor]', fonte)
            .map(function (item) { return item.getAttribute('data-nome'); })
            .filter(function (nome) { return nome && nome.trim() !== ''; });
        if (vencedores.length === 0) {
            return null;
        }
        return {
            rodada: fonte.getAttribute('data-rodada') || '',
            vencedores: vencedores
        };
    }

    function indiceVisualAleatorio(tamanho) {
        if (window.crypto && window.crypto.getRandomValues) {
            var valores = new Uint32Array(1);
            window.crypto.getRandomValues(valores);
            return valores[0] % tamanho;
        }
        return Math.floor(Math.random() * tamanho);
    }

    function limparSuspense() {
        if (temporizadorSuspense) {
            window.clearTimeout(temporizadorSuspense);
            temporizadorSuspense = null;
        }
        if (quadroSuspense) {
            window.cancelAnimationFrame(quadroSuspense);
            quadroSuspense = null;
        }
    }

    function limparDestaqueDaUrl() {
        if (!window.history || !window.URL) {
            return;
        }
        try {
            var url = new URL(window.location.href);
            if (url.searchParams.has('destaque')) {
                url.searchParams.delete('destaque');
                window.history.replaceState({}, '', url.pathname + url.search + url.hash);
            }
        } catch (erro) {
            /* navegadores antigos apenas mantêm a URL como está */
        }
    }

    function revelarSuspense(dados) {
        if (!suspenseResultado || !suspenseVencedores) {
            return;
        }
        esconder(suspenseContagem);
        esconder(suspenseRoleta);
        suspenseVencedores.innerHTML = '';
        dados.vencedores.forEach(function (nome, indice) {
            var item = document.createElement('li');
            item.textContent = nome;
            item.style.animationDelay = prefereMenosMovimento() ? '0s' : (indice * 0.12) + 's';
            suspenseVencedores.appendChild(item);
        });
        mostrar(suspenseResultado);
        mostrar(suspenseAcoes);
        if (suspenseTitulo) {
            suspenseTitulo.textContent = dados.vencedores.length === 1
                ? 'Temos vencedor!'
                : 'Temos vencedores!';
        }
        if (suspenseStatus) {
            suspenseStatus.textContent = 'Vencedor' + (dados.vencedores.length === 1 ? ': ' : 'es: ')
                + dados.vencedores.join(', ') + '.';
        }
        var painel = suspense.querySelector('.sorteios-suspense');
        if (painel) {
            painel.setAttribute('tabindex', '-1');
            painel.focus({ preventScroll: true });
        }
    }

    function rodarRoleta(dados) {
        var nomes = nomesElegiveis();
        if (nomes.length === 0) {
            nomes = dados.vencedores.slice();
        }
        if (!suspenseRoleta || nomes.length === 0) {
            revelarSuspense(dados);
            return;
        }
        var inicio = window.performance.now();
        var duracao = 2400;
        var ultimaTroca = 0;

        function quadro(agora) {
            var progresso = Math.min(1, (agora - inicio) / duracao);
            var suavizado = 1 - Math.pow(1 - progresso, 3);
            var intervalo = 55 + (suavizado * 230);
            if (agora - ultimaTroca >= intervalo) {
                suspenseRoleta.textContent = nomes[indiceVisualAleatorio(nomes.length)];
                ultimaTroca = agora;
            }
            if (progresso < 1) {
                quadroSuspense = window.requestAnimationFrame(quadro);
            } else {
                revelarSuspense(dados);
            }
        }

        quadroSuspense = window.requestAnimationFrame(quadro);
    }

    function abrirSuspense() {
        if (!suspense) {
            return;
        }
        var dados = lerDadosDestaque();
        if (!dados) {
            return;
        }
        limparSuspense();
        if (suspenseTitulo) {
            suspenseTitulo.textContent = 'Preparando a revelação…';
        }
        esconder(suspenseResultado);
        esconder(suspenseAcoes);
        if (suspenseVencedores) {
            suspenseVencedores.innerHTML = '';
        }
        if (suspenseStatus) {
            suspenseStatus.textContent = 'Sorteio realizado. Preparando a revelação.';
        }
        mostrar(suspense);
        travarPagina(true);

        if (prefereMenosMovimento()) {
            revelarSuspense(dados);
            return;
        }

        if (suspenseContagem) {
            suspenseContagem.textContent = '3';
            suspenseContagem.style.animation = 'none';
            void suspenseContagem.offsetWidth;
            suspenseContagem.style.animation = '';
            mostrar(suspenseContagem);
        }
        esconder(suspenseRoleta);

        var restante = 3;
        function contar() {
            if (restante > 1) {
                restante--;
                if (suspenseContagem) {
                    suspenseContagem.textContent = String(restante);
                    suspenseContagem.style.animation = 'none';
                    void suspenseContagem.offsetWidth;
                    suspenseContagem.style.animation = '';
                }
                temporizadorSuspense = window.setTimeout(contar, 1000);
                return;
            }
            esconder(suspenseContagem);
            mostrar(suspenseRoleta);
            if (suspenseStatus) {
                suspenseStatus.textContent = 'Sorteando os nomes.';
            }
            rodarRoleta(dados);
        }
        temporizadorSuspense = window.setTimeout(contar, 1000);
    }

    function fecharSuspense() {
        if (!suspense) {
            return;
        }
        limparSuspense();
        esconder(suspense);
        travarPagina(false);
        limparDestaqueDaUrl();
    }

    function redimensionarConfete() {
        if (!canvasConfete) {
            return;
        }
        canvasConfete.width = window.innerWidth;
        canvasConfete.height = window.innerHeight;
    }

    function soltarConfete() {
        if (!canvasConfete || prefereMenosMovimento()) {
            return;
        }
        redimensionarConfete();
        var contexto = canvasConfete.getContext('2d');
        if (!contexto) {
            return;
        }
        if (quadroConfete) {
            window.cancelAnimationFrame(quadroConfete);
            quadroConfete = null;
        }
        contexto.clearRect(0, 0, canvasConfete.width, canvasConfete.height);

        var cores = ['#A855F7', '#D946EF', '#FBBF24', '#FFFFFF', '#C084FC', '#8B5CF6'];
        var pecas = [];
        for (var i = 0; i < 150; i++) {
            pecas.push({
                x: Math.random() * canvasConfete.width,
                y: -20 - (Math.random() * canvasConfete.height * 0.35),
                largura: 5 + Math.random() * 7,
                altura: 8 + Math.random() * 9,
                velocidadeX: -1.8 + Math.random() * 3.6,
                velocidadeY: 2.4 + Math.random() * 4.2,
                rotacao: Math.random() * Math.PI,
                giro: -0.12 + Math.random() * 0.24,
                cor: cores[Math.floor(Math.random() * cores.length)]
            });
        }
        var inicio = window.performance.now();
        var duracao = 3600;

        function animar(agora) {
            var decorrido = agora - inicio;
            contexto.clearRect(0, 0, canvasConfete.width, canvasConfete.height);
            pecas.forEach(function (peca) {
                peca.x += peca.velocidadeX;
                peca.y += peca.velocidadeY;
                peca.rotacao += peca.giro;
                contexto.save();
                contexto.translate(peca.x, peca.y);
                contexto.rotate(peca.rotacao);
                contexto.fillStyle = peca.cor;
                contexto.fillRect(-peca.largura / 2, -peca.altura / 2, peca.largura, peca.altura);
                contexto.restore();
            });
            if (decorrido < duracao) {
                quadroConfete = window.requestAnimationFrame(animar);
            } else {
                contexto.clearRect(0, 0, canvasConfete.width, canvasConfete.height);
            }
        }
        quadroConfete = window.requestAnimationFrame(animar);
    }

    function renderizarApresentacao() {
        if (!dadosApresentacao || !apresentacaoNome) {
            return;
        }
        var total = dadosApresentacao.vencedores.length;
        var indice = Math.max(0, Math.min(indiceApresentacao, total - 1));
        indiceApresentacao = indice;

        apresentacaoNome.textContent = dadosApresentacao.vencedores[indice];
        if (apresentacaoRodada) {
            apresentacaoRodada.textContent = dadosApresentacao.rodada
                ? 'Rodada ' + dadosApresentacao.rodada
                : '';
        }
        if (apresentacaoContador) {
            apresentacaoContador.textContent = 'Vencedor ' + (indice + 1) + ' de ' + total;
        }
        if (apresentacaoAnterior) {
            apresentacaoAnterior.disabled = indice === 0;
        }
        if (apresentacaoProximo) {
            apresentacaoProximo.disabled = indice >= total - 1;
        }
        if (!prefereMenosMovimento()) {
            apresentacaoNome.classList.remove('is-revelando');
            void apresentacaoNome.offsetWidth;
            apresentacaoNome.classList.add('is-revelando');
        }
        soltarConfete();
    }

    function entrarEmTelaCheia() {
        if (apresentacao && typeof apresentacao.requestFullscreen === 'function' && !document.fullscreenElement) {
            var tentativa = apresentacao.requestFullscreen();
            if (tentativa && typeof tentativa.catch === 'function') {
                tentativa.catch(function () {
                    /* overlay em tela cheia via CSS continua funcionando */
                });
            }
        }
    }

    function abrirApresentacao() {
        if (!apresentacao) {
            return;
        }
        var dados = lerDadosApresentacao();
        if (!dados) {
            mostrarToast('Realize um sorteio para abrir o modo apresentação.');
            return;
        }
        if (suspense && !suspense.classList.contains('hidden')) {
            fecharSuspense();
        }
        dadosApresentacao = dados;
        indiceApresentacao = 0;
        mostrar(apresentacao);
        travarPagina(true);
        renderizarApresentacao();
        var sair = porId('fechar-apresentacao');
        if (sair) {
            sair.focus({ preventScroll: true });
        }
        entrarEmTelaCheia();
    }

    function fecharApresentacao() {
        if (!apresentacao) {
            return;
        }
        esconder(apresentacao);
        travarPagina(false);
        if (quadroConfete) {
            window.cancelAnimationFrame(quadroConfete);
            quadroConfete = null;
        }
        if (canvasConfete) {
            var contexto = canvasConfete.getContext('2d');
            if (contexto) {
                contexto.clearRect(0, 0, canvasConfete.width, canvasConfete.height);
            }
        }
        if (document.fullscreenElement && document.exitFullscreen) {
            var saida = document.exitFullscreen();
            if (saida && typeof saida.catch === 'function') {
                saida.catch(function () { /* ignorado */ });
            }
        }
    }

    document.addEventListener('click', function (evento) {
        var gatilho = evento.target.closest('[data-modo-apresentacao]');
        if (gatilho && !gatilho.disabled) {
            abrirApresentacao();
        }
    });

    var fecharSuspenseBtn = porId('fechar-suspense');
    if (fecharSuspenseBtn) {
        fecharSuspenseBtn.addEventListener('click', fecharSuspense);
    }
    var reabrir = porId('reabrir-revelacao');
    if (reabrir) {
        reabrir.addEventListener('click', abrirSuspense);
    }
    var fecharApresentacaoBtn = porId('fechar-apresentacao');
    if (fecharApresentacaoBtn) {
        fecharApresentacaoBtn.addEventListener('click', fecharApresentacao);
    }
    if (apresentacaoAnterior) {
        apresentacaoAnterior.addEventListener('click', function () {
            if (indiceApresentacao > 0) {
                indiceApresentacao--;
                renderizarApresentacao();
            }
        });
    }
    if (apresentacaoProximo) {
        apresentacaoProximo.addEventListener('click', function () {
            if (dadosApresentacao && indiceApresentacao < dadosApresentacao.vencedores.length - 1) {
                indiceApresentacao++;
                renderizarApresentacao();
            }
        });
    }

    document.addEventListener('keydown', function (evento) {
        if (apresentacao && !apresentacao.classList.contains('hidden')) {
            if (evento.key === 'ArrowLeft' && indiceApresentacao > 0) {
                indiceApresentacao--;
                renderizarApresentacao();
            } else if (evento.key === 'ArrowRight' && dadosApresentacao
                && indiceApresentacao < dadosApresentacao.vencedores.length - 1) {
                indiceApresentacao++;
                renderizarApresentacao();
            } else if (evento.key === 'Escape') {
                fecharApresentacao();
            }
        } else if (suspense && !suspense.classList.contains('hidden') && evento.key === 'Escape') {
            fecharSuspense();
        }
    });

    document.addEventListener('fullscreenchange', function () {
        if (document.fullscreenElement) {
            entrouEmTelaCheia = true;
        } else if (entrouEmTelaCheia) {
            entrouEmTelaCheia = false;
            if (apresentacao && !apresentacao.classList.contains('hidden')) {
                fecharApresentacao();
            }
        }
    });

    window.addEventListener('resize', function () {
        if (apresentacao && !apresentacao.classList.contains('hidden')) {
            redimensionarConfete();
        }
    });

    // ---------------------------------------------------------------------
    // Inicialização
    // ---------------------------------------------------------------------
    agendarToastsIniciais();
    if (porId('resultado-destaque')) {
        window.setTimeout(abrirSuspense, 240);
    }
})();
