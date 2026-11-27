/* Folha de compartilhamento reutilizável (estilo YouTube / apps móveis). */
(function () {
    'use strict';

    function modalAtual() {
        return document.getElementById('seplag-share-modal');
    }

    function urlDoModal(modal) {
        var valor = modal.getAttribute('data-share-url') || window.location.href;
        try {
            return new URL(valor, window.location.origin).href;
        } catch (erro) {
            return window.location.href;
        }
    }

    function tituloDoModal(modal) {
        return modal.getAttribute('data-share-title') || document.title;
    }

    function linksDeCompartilhamento(titulo, url, privado) {
        var texto = encodeURIComponent(titulo);
        var link = encodeURIComponent(url);
        var corpoEmail = 'Confira este evento na plataforma SEPLAG-MT: ' + titulo + '\n' + url;
        if (privado) {
            corpoEmail += '\n\nEste evento é privado. Entre na plataforma com sua conta para visualizar os detalhes e solicitar participação.';
        }
        return {
            whatsapp: 'https://wa.me/?text=' + texto + '%20' + link,
            facebook: 'https://www.facebook.com/sharer/sharer.php?u=' + link,
            x: 'https://twitter.com/intent/tweet?text=' + texto + '&url=' + link,
            telegram: 'https://t.me/share/url?url=' + link + '&text=' + texto,
            linkedin: 'https://www.linkedin.com/sharing/share-offsite/?url=' + link,
            email: 'mailto:?subject=' + encodeURIComponent('Convite: ' + titulo)
                + '&body=' + encodeURIComponent(corpoEmail)
        };
    }

    function feedback(modal, mensagem) {
        var alvo = modal.querySelector('#seplag-share-feedback');
        if (!alvo) return;
        alvo.textContent = mensagem || '';
        if (mensagem) {
            window.clearTimeout(feedback._timer);
            feedback._timer = window.setTimeout(function () {
                alvo.textContent = '';
            }, 3200);
        }
    }

    function copiarLink(modal) {
        var url = urlDoModal(modal);
        var concluido = function () {
            feedback(modal, 'Link copiado para a área de transferência.');
            var rotulo = modal.querySelector('#seplag-share-copy-label');
            if (rotulo) {
                var original = rotulo.textContent;
                rotulo.textContent = 'Link copiado!';
                window.setTimeout(function () { rotulo.textContent = original; }, 2200);
            }
        };
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(url).then(concluido).catch(function () {
                copiarComFallback(modal, url, concluido);
            });
            return;
        }
        copiarComFallback(modal, url, concluido);
    }

    function copiarComFallback(modal, url, concluido) {
        var campo = document.createElement('textarea');
        campo.value = url;
        campo.setAttribute('readonly', '');
        campo.style.position = 'fixed';
        campo.style.opacity = '0';
        document.body.appendChild(campo);
        campo.select();
        try {
            document.execCommand('copy');
            concluido();
        } catch (erro) {
            feedback(modal, 'Não foi possível copiar. Selecione o link manualmente.');
        } finally {
            document.body.removeChild(campo);
        }
    }

    function abrir(modal) {
        if (!modal) return;
        modal.hidden = false;
        document.body.style.overflow = 'hidden';
        var primeiro = modal.querySelector('.seplag-share__close');
        if (primeiro) primeiro.focus();
    }

    function fechar(modal) {
        if (!modal) return;
        modal.hidden = true;
        document.body.style.overflow = '';
    }

    function compartilharNativo(modal) {
        var url = urlDoModal(modal);
        var titulo = tituloDoModal(modal);
        if (!navigator.share) {
            copiarLink(modal);
            return;
        }
        navigator.share({ title: titulo, text: titulo, url: url }).then(function () {
            fechar(modal);
        }).catch(function (erro) {
            if (erro && erro.name !== 'AbortError') {
                copiarLink(modal);
            }
        });
    }

    function configurarNativo(modal) {
        var botao = modal.querySelector('[data-share-action="native"]');
        if (!botao) return;
        if (!navigator.share) {
            botao.hidden = true;
        }
    }

    document.addEventListener('click', function (evento) {
        var gatilho = evento.target.closest('[data-share-open]');
        if (gatilho) {
            evento.preventDefault();
            var modal = modalAtual();
            if (gatilho.hasAttribute('data-share-title')) {
                modal.setAttribute('data-share-title', gatilho.getAttribute('data-share-title'));
            }
            if (gatilho.hasAttribute('data-share-url')) {
                modal.setAttribute('data-share-url', gatilho.getAttribute('data-share-url'));
            }
            modal.setAttribute('data-share-private', gatilho.getAttribute('data-share-private') === 'true' ? 'true' : 'false');
            configurarNativo(modal);
            abrir(modal);
            return;
        }

        var modal = modalAtual();
        if (!modal || modal.hidden) return;

        var acao = evento.target.closest('[data-share-action]');
        if (acao) {
            var tipo = acao.getAttribute('data-share-action');
            if (tipo === 'close') {
                evento.preventDefault();
                fechar(modal);
            } else if (tipo === 'copy') {
                evento.preventDefault();
                copiarLink(modal);
            } else if (tipo === 'native') {
                evento.preventDefault();
                compartilharNativo(modal);
            }
            return;
        }

        var rede = evento.target.closest('[data-share-network]');
        if (rede) {
            evento.preventDefault();
            var mapa = linksDeCompartilhamento(
                tituloDoModal(modal),
                urlDoModal(modal),
                rede.getAttribute('data-share-network') === 'email'
                    && modal.getAttribute('data-share-private') === 'true'
            );
            var destino = mapa[rede.getAttribute('data-share-network')];
            if (destino) {
                if (destino.indexOf('mailto:') === 0) {
                    window.location.href = destino;
                } else {
                    window.open(destino, '_blank', 'noopener,noreferrer,width=760,height=620');
                }
            }
        }
    });

    document.addEventListener('keydown', function (evento) {
        if (evento.key === 'Escape') {
            var modal = modalAtual();
            if (modal && !modal.hidden) {
                fechar(modal);
            }
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
        configurarNativo(modalAtual());
    });
})();
