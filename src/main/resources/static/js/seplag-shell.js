/* ==========================================================================
   SEPLAG Eventos — shell v2
   Controla o drawer de navegação em telas menores e o botão de
   mostrar/ocultar senha. Sem dependências, sem build. Não interfere em
   formulários, validação ou envio.
   ========================================================================== */

/* Mostrar/ocultar senha — um botão independente por campo. */
(function () {
    'use strict';

    var toggles = document.querySelectorAll('[data-seplag-password-toggle]');

    Array.prototype.forEach.call(toggles, function (toggle) {
        var inputId = toggle.getAttribute('aria-controls');
        var input = inputId ? document.getElementById(inputId) : null;
        var rotuloMostrar = toggle.getAttribute('data-label-show') || 'Mostrar senha';
        var rotuloOcultar = toggle.getAttribute('data-label-hide') || 'Ocultar senha';

        if (!input) {
            var wrapper = toggle.closest('.seplag-password');
            input = wrapper ? wrapper.querySelector('input') : null;
        }

        if (!input) {
            return;
        }

        toggle.addEventListener('click', function () {
            var visivel = input.type === 'password';

            input.type = visivel ? 'text' : 'password';

            var rotulo = visivel ? rotuloOcultar : rotuloMostrar;
            toggle.setAttribute('aria-label', rotulo);
            toggle.setAttribute('aria-pressed', visivel ? 'true' : 'false');
            toggle.setAttribute('title', rotulo);

            var icone = toggle.querySelector('i');
            if (icone) {
                icone.classList.toggle('fa-eye', !visivel);
                icone.classList.toggle('fa-eye-slash', visivel);
            }
        });
    });
})();

(function () {
    'use strict';

    var body = document.body;
    var toggle = document.querySelector('[data-seplag-menu-toggle]');
    var backdrop = document.querySelector('[data-seplag-backdrop]');
    var sidebar = document.getElementById('seplag-sidebar');

    if (!toggle || !sidebar) {
        return;
    }

    var DESKTOP = 1024;
    var isOpen = false;

    function setOpen(open, returnFocus) {
        isOpen = open;
        body.classList.toggle('seplag-shell--open', open);
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        toggle.setAttribute('aria-label', open ? 'Fechar menu de navegação' : 'Abrir menu de navegação');

        if (open) {
            var first = sidebar.querySelector('a, button');
            if (first) {
                first.focus();
            }
        } else if (returnFocus) {
            toggle.focus();
        }
    }

    toggle.addEventListener('click', function () {
        setOpen(!isOpen, false);
    });

    if (backdrop) {
        backdrop.addEventListener('click', function () {
            setOpen(false, true);
        });
    }

    sidebar.addEventListener('click', function (event) {
        var link = event.target.closest('a');
        if (link && window.innerWidth < DESKTOP) {
            setOpen(false, false);
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && isOpen) {
            setOpen(false, true);
        }
    });

    window.addEventListener('resize', function () {
        if (window.innerWidth >= DESKTOP && isOpen) {
            setOpen(false, false);
        }
    });
})();

/* Modal de compartilhamento estilo YouTube */
(function () {
    'use strict';

    var dialog = document.getElementById('seplagShareModal');
    if (!dialog) {
        return;
    }

    var titleEl = document.getElementById('shareModalEventoNome');
    var inputEl = document.getElementById('shareLinkInput');
    var copyBtn = document.getElementById('shareCopyBtn');
    var feedbackEl = document.getElementById('shareCopyFeedback');
    var nativeBtn = document.getElementById('shareNativeBtn');
    var whatsAppEl = document.getElementById('shareWhatsApp');
    var facebookEl = document.getElementById('shareFacebook');
    var xEl = document.getElementById('shareX');
    var linkedInEl = document.getElementById('shareLinkedIn');
    var telegramEl = document.getElementById('shareTelegram');
    var emailEl = document.getElementById('shareEmail');

    var currentUrl = '';
    var currentTitle = '';
    var copyTimer = null;

    function resolveUrl(raw) {
        if (!raw) {
            return window.location.href;
        }
        try {
            return new URL(raw, window.location.origin).toString();
        } catch (e) {
            return window.location.href;
        }
    }

    function resetCopyState() {
        if (copyTimer) {
            window.clearTimeout(copyTimer);
            copyTimer = null;
        }
        if (copyBtn) {
            copyBtn.textContent = 'Copiar';
            copyBtn.classList.remove('is-copied');
        }
        if (feedbackEl) {
            feedbackEl.textContent = '';
        }
    }

    function openShareModal(url, title) {
        currentUrl = resolveUrl(url);
        currentTitle = title || 'Evento SEPLAG-MT';
        resetCopyState();

        if (titleEl) {
            titleEl.textContent = currentTitle;
        }
        if (inputEl) {
            inputEl.value = currentUrl;
        }

        var textoShare = 'Confira este evento na SEPLAG-MT: ' + currentTitle;
        var encUrl = encodeURIComponent(currentUrl);
        var encTexto = encodeURIComponent(textoShare);
        var encCompleto = encodeURIComponent(textoShare + ' ' + currentUrl);

        if (whatsAppEl) whatsAppEl.href = 'https://wa.me/?text=' + encCompleto;
        if (facebookEl) facebookEl.href = 'https://www.facebook.com/sharer/sharer.php?u=' + encUrl;
        if (xEl) xEl.href = 'https://twitter.com/intent/tweet?url=' + encUrl + '&text=' + encTexto;
        if (linkedInEl) linkedInEl.href = 'https://www.linkedin.com/sharing/share-offsite/?url=' + encUrl;
        if (telegramEl) telegramEl.href = 'https://t.me/share/url?url=' + encUrl + '&text=' + encTexto;
        if (emailEl) {
            emailEl.href = 'mailto:?subject=' + encodeURIComponent('Convite: ' + currentTitle)
                + '&body=' + encCompleto;
        }
        if (nativeBtn) {
            nativeBtn.classList.toggle('hidden', typeof navigator.share !== 'function');
        }

        if (typeof dialog.showModal === 'function') {
            if (!dialog.open) {
                dialog.showModal();
            }
        } else {
            dialog.setAttribute('open', 'open');
        }
    }

    function closeShareModal() {
        if (typeof dialog.close === 'function') {
            if (dialog.open) {
                dialog.close();
            }
        } else {
            dialog.removeAttribute('open');
        }
    }

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest('[data-share-trigger], [data-share-open]');
        if (trigger) {
            event.preventDefault();
            openShareModal(
                trigger.getAttribute('data-share-url'),
                trigger.getAttribute('data-share-title')
            );
            return;
        }
        if (event.target.closest('[data-share-close]') || event.target === dialog) {
            closeShareModal();
        }
    });

    if (copyBtn) {
        copyBtn.addEventListener('click', function () {
            if (!currentUrl) return;
            var finish = function () {
                copyBtn.textContent = 'Copiado!';
                copyBtn.classList.add('is-copied');
                if (feedbackEl) {
                    feedbackEl.textContent = 'Link copiado para a área de transferência.';
                }
                copyTimer = window.setTimeout(resetCopyState, 2500);
            };
            if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
                navigator.clipboard.writeText(currentUrl).then(finish, function () {
                    if (inputEl) {
                        inputEl.select();
                        document.execCommand('copy');
                        finish();
                    }
                });
            } else if (inputEl) {
                inputEl.select();
                document.execCommand('copy');
                finish();
            }
        });
    }

    if (nativeBtn) {
        nativeBtn.addEventListener('click', function () {
            if (typeof navigator.share === 'function') {
                navigator.share({
                    title: currentTitle,
                    text: 'Confira este evento na SEPLAG-MT: ' + currentTitle,
                    url: currentUrl
                }).catch(function () {});
            }
        });
    }
})();
