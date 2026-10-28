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
